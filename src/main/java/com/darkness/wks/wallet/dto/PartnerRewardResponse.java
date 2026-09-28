package com.darkness.wks.wallet.dto;

import com.darkness.wks.wallet.PartnerRewardService;

/**
 * POST /api/wallet/partner-rewards 응답. api-spec.md §12.
 * {@code rewardGranted} 는 이번 호출로 지급됐을 때만 채워진다 — 이미 받았거나 모르는 코드면 {@code null} 이고
 * 에러가 아니다(출석 체크와 같은 이유로, 링크를 다시 눌러도 자연스럽게 처리한다).
 */
public record PartnerRewardResponse(RewardGranted rewardGranted, int balance) {

    /** 로그인 응답의 rewardGranted 와 같은 모양이다 */
    public record RewardGranted(String partnerName, int amount) {
        public static RewardGranted from(PartnerRewardService.Granted granted) {
            return new RewardGranted(granted.partnerName(), granted.amount());
        }
    }
}
