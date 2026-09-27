package com.darkness.wks.wallet.dto;

/** GET /api/wallet 응답. api-spec.md §12. */
public record WalletResponse(int balance, boolean canCheckInToday) {
}
