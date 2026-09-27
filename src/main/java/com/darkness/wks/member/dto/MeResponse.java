package com.darkness.wks.member.dto;

/**
 * GET /api/me 응답. api-spec.md §9.
 * {@code hasDatingProfile} 은 소개팅 프로필 등록 여부, {@code threadBalance} 는 실 잔액이다.
 */
public record MeResponse(
        Long memberId,
        boolean hasResult,
        boolean hasDatingProfile,
        int threadBalance
) {
}
