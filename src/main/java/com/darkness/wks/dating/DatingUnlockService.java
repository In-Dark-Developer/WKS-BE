package com.darkness.wks.dating;

import com.darkness.wks.dating.dto.DatingUnlockResponse;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 소개팅 카드 정보 해금 (plan.md §8.5). 차감·해금 기록({@link DatingUnlockChargeService}, 짧은
 * 트랜잭션)과 값 조회를 분리한다 — {@code REASON} 은 {@link DatingReasonService#getOrCreate} 가 LLM을
 * 호출할 수 있는데(최대 30초), 그동안 실 차감 트랜잭션·advisory lock을 붙잡고 있으면 커넥션 풀이
 * 마른다(#94 인수인계, CompatibilityReasonService 와 같은 이유).
 */
@Service
@RequiredArgsConstructor
public class DatingUnlockService {

    private final DatingUnlockChargeService chargeService;
    private final WalletService walletService;
    private final DatingPhotoService photoService;
    private final DatingReasonService reasonService;

    /**
     * 고른 필드를 한 번에 해금한다. 중복 필드는 한 번으로 친다(EnumSet). {@code REASON} 은 차감보다
     * **먼저** 생성한다 — 차감을 커밋한 뒤 LLM 이 실패하면 실만 빠지고 값은 못 받는 것으로 보인다(#121).
     * 생성 결과는 추천행마다 한 번만 캐시되므로 차감 없이 생성만 되는 경우도 LLM 호출은 추천행당 최대 1회다.
     */
    public DatingUnlockResponse unlock(Long viewerMemberId, UUID candidateId, Collection<DatingUnlockField> requested) {
        Set<DatingUnlockField> fields = EnumSet.copyOf(requested);
        String reason = fields.contains(DatingUnlockField.REASON)
                ? reasonService.getOrCreate(viewerMemberId, candidateId) : null;
        DatingProfile candidate = chargeService.chargeAndMarkUnlocked(viewerMemberId, candidateId, fields);
        Map<String, String> values = new LinkedHashMap<>();
        for (DatingUnlockField field : fields) {
            values.put(field.name(), switch (field) {
                case PHOTO -> photoService.originalUrl(candidate.getPhoto());
                case NAME -> candidate.getName();
                case DEPARTMENT -> candidate.getDepartment();
                case REASON -> reason;
            });
        }
        return new DatingUnlockResponse(values, walletService.getBalance(viewerMemberId));
    }
}
