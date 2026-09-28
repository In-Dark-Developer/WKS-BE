package com.darkness.wks.compatibility;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.dto.CompatibilityReasonResponse;
import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.CompatibilityReason;
import com.darkness.wks.saju.CompatibilityReasonBank;
import com.darkness.wks.saju.CompatibilityReasonGenerator;
import com.darkness.wks.saju.SajuPillars;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 궁합 상세 이유 (plan §1.2). 처음 열어볼 때 사전 생성본에서 고르거나(#131) LLM 으로 만들어 캐싱하고,
 * 그 뒤로는 저장된 글만 돌려준다 (FR-CP-11).
 * <p>
 * 일부러 트랜잭션을 걸지 않는다. LLM 호출(최대 30초×2)이 트랜잭션 안에 있으면 그동안 DB 연결을 잡아 두어
 * 공유가 몰릴 때 풀이 마른다. 조회·저장은 리포지토리가 각각 짧은 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class CompatibilityReasonService {

    private final CompatibilityRepository compatibilityRepository;
    private final CompatibilityReasonGenerator generator;

    public CompatibilityReasonResponse getReason(Long id) {
        Compatibility c = find(id);
        if (c.hasReason()) {
            return CompatibilityReasonResponse.from(c);
        }
        List<Compatibility> siblings = compatibilityRepository.findAllByOriginIdWithResults(c.getOrigin().getId());
        CompatibilityReason reason = generator.generate(
                toPillars(c.getOrigin()), toPillars(c.getGuest()), c.getScore(), c.getTier().korean(),
                ordinal(c, siblings), shownSentences(siblings));
        int saved = compatibilityRepository.saveReasonIfAbsent(id, reason.why(), reason.together(), reason.conflict());
        if (saved == 0) { // 동시에 연 다른 사람이 먼저 저장했다. 둘이 같은 글을 보도록 저장된 쪽을 돌려준다
            return CompatibilityReasonResponse.from(find(id));
        }
        return new CompatibilityReasonResponse(reason.why(), reason.together(), reason.conflict());
    }

    /**
     * 공유자(origin)별 시작 자리 + 공유자의 궁합 중 같은 조합(기운·많은 기운·유형)이 이 행보다 먼저 몇 개 있었는지.
     * 같은 조합의 친구가 여럿이어도 다른 변형을 보고, 공유자마다 시작이 달라 특정 변형만 몰리지 않는다.
     * 행 순서·공유자 id 로만 정하므로 두 사람은 여전히 같은 글을 본다.
     * ponytail: 공유자 궁합 전부를 읽는다. 축제 규모(사람당 수십 건)면 충분하고, 커지면 조합 키 컬럼을 둔다.
     */
    private static int ordinal(Compatibility c, List<Compatibility> siblings) {
        String key = key(c);
        long earlier = siblings.stream()
                .filter(other -> other.getId() < c.getId() && key.equals(key(other)))
                .count();
        return CompatibilityReasonBank.start(c.getOrigin().getId()) + (int) earlier;
    }

    /** 공유자가 다른 궁합에서 이미 읽은 문장. 사전 생성본이 첫 문장을 정형구로 쓰는 일이 잦아 변형 선택 때 피한다 */
    private static Set<String> shownSentences(List<Compatibility> siblings) {
        Set<String> shown = new HashSet<>();
        for (Compatibility other : siblings) {
            if (other.hasReason()) {
                shown.addAll(CompatibilityReasonBank.sentences(other.getReasonWhy()));
                shown.addAll(CompatibilityReasonBank.sentences(other.getReasonTogether()));
                shown.addAll(CompatibilityReasonBank.sentences(other.getReasonConflict()));
            }
        }
        return shown;
    }

    private static String key(Compatibility c) {
        return CompatibilityReasonBank.key(toPillars(c.getOrigin()), toPillars(c.getGuest()), c.getTier().korean());
    }

    private Compatibility find(Long id) {
        return compatibilityRepository.findByIdWithResults(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPATIBILITY_NOT_FOUND));
    }

    private static SajuPillars toPillars(Result r) {
        return new SajuPillars(r.getYearPillar(), r.getMonthPillar(), r.getDayPillar(), r.getHourPillar());
    }
}
