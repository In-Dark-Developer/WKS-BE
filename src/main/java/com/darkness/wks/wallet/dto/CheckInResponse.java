package com.darkness.wks.wallet.dto;

/**
 * POST /api/wallet/check-in 응답. api-spec.md §12.
 * {@code checkedIn} 이 false 면 오늘 이미 출석해서 이번 호출로는 지급되지 않았다는 뜻 — 에러가 아니라
 * 200 으로 응답한다(버튼을 다시 눌러도 자연스럽게 처리하기 위해, 2026-09-26 결정).
 */
public record CheckInResponse(boolean checkedIn, int balance) {
}
