package com.darkness.wks.compatibility;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.dto.CompatibilityReasonResponse;
import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.CompatibilityReason;
import com.darkness.wks.saju.CompatibilityReasonGenerator;
import com.darkness.wks.saju.SajuPillars;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 궁합 상세 이유 (plan §1.2). 처음 열어볼 때 LLM 으로 만들어 캐싱하고, 그 뒤로는 LLM 호출 0회 (FR-CP-11).
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
        CompatibilityReason reason = generator.generate(
                toPillars(c.getOrigin()), toPillars(c.getGuest()), c.getScore(), c.getTier().korean());
        int saved = compatibilityRepository.saveReasonIfAbsent(id, reason.why(), reason.together(), reason.conflict());
        if (saved == 0) { // 동시에 연 다른 사람이 먼저 저장했다. 둘이 같은 글을 보도록 저장된 쪽을 돌려준다
            return CompatibilityReasonResponse.from(find(id));
        }
        return new CompatibilityReasonResponse(reason.why(), reason.together(), reason.conflict());
    }

    private Compatibility find(Long id) {
        return compatibilityRepository.findByIdWithResults(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPATIBILITY_NOT_FOUND));
    }

    private static SajuPillars toPillars(Result r) {
        return new SajuPillars(r.getYearPillar(), r.getMonthPillar(), r.getDayPillar(), r.getHourPillar());
    }
}
