package com.darkness.wks.wallet.dto;

import java.util.List;

/**
 * GET /api/wallet 응답. api-spec.md §12. {@code partnerRewards} 는 이 계정이 받은 제휴 코드(예: {@code FESTIVAL}) —
 * 실 현황 모달이 "지급 완료"를 표시하는 데 쓴다(2026-09-30 QA).
 */
public record WalletResponse(int balance, boolean canCheckInToday, List<String> partnerRewards) {
}
