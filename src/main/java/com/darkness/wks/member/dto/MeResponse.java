package com.darkness.wks.member.dto;

/**
 * GET /api/me 응답. api-spec.md §9.
 * {@code hasDatingProfile}·{@code threadBalance} 는 소개팅·실 기능이 구현되기 전까지 항상
 * {@code false}·{@code 0} 이다 — 필드는 미리 둬서 나중에 값만 채워지게 한다.
 */
public record MeResponse(
        Long memberId,
        boolean hasResult,
        boolean hasDatingProfile,
        int threadBalance
) {
}
