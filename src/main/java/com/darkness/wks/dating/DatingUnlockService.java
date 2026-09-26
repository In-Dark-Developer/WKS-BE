package com.darkness.wks.dating;

import com.darkness.wks.dating.dto.DatingUnlockResponse;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public DatingUnlockResponse unlock(Long viewerMemberId, UUID candidateId, DatingUnlockField field) {
        DatingProfile candidate = chargeService.chargeAndMarkUnlocked(viewerMemberId, candidateId, field);
        String value = switch (field) {
            case PHOTO -> photoService.originalUrl(candidate.getPhoto());
            case NAME -> candidate.getName();
            case DEPARTMENT -> candidate.getDepartment();
            case REASON -> reasonService.getOrCreate(viewerMemberId, candidateId);
        };
        return new DatingUnlockResponse(field.name(), value, walletService.getBalance(viewerMemberId));
    }
}
