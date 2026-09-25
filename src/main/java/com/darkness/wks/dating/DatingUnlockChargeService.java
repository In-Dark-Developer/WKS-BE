package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.dating.entity.DatingRecommendation;
import com.darkness.wks.wallet.LedgerReason;
import com.darkness.wks.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * {@link DatingUnlockService} 에서 분리한 별도 빈이다 — 같은 클래스 안에서 {@code @Transactional}
 * 메서드를 호출하면(self-invocation) 스프링 프록시를 거치지 않아 트랜잭션이 실제로 시작되지 않는다.
 */
@Service
@RequiredArgsConstructor
class DatingUnlockChargeService {

    private final DatingRecommendationRepository recommendationRepository;
    private final WalletService walletService;

    /**
     * 실 차감·해금 기록을 한 트랜잭션으로 커밋한다(FR-DT-06). 이미 해금한 필드면 차감하지 않는다.
     * {@code ref_id} 는 "추천행ID:필드" — 후보 하나에 필드가 4개라 후보 UUID만으로는 구분이 안 된다.
     */
    @Transactional
    DatingProfile chargeAndMarkUnlocked(Long viewerMemberId, UUID candidateId, DatingUnlockField field) {
        DatingRecommendation recommendation = recommendationRepository
                .findActiveWithCandidate(viewerMemberId, candidateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND));
        if (!recommendation.isUnlocked(field)) {
            walletService.debit(viewerMemberId, LedgerReason.UNLOCK,
                    recommendation.getId() + ":" + field.name(), field.cost());
            recommendation.unlock(field);
        }
        DatingProfile candidate = recommendation.getCandidate();
        candidate.getPhoto().getObjectKey(); // LAZY 필드를 트랜잭션 안에서 미리 채워 둔다(open-in-view: false)
        return candidate;
    }
}
