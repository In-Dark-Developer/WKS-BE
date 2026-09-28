package com.darkness.wks.auth.dto;

/**
 * POST /api/auth/kakao 응답. api-spec.md §9.
 * 토큰은 응답 바디에 안 실린다 — {@code Set-Cookie}(HttpOnly)로만 내려간다(2026-09-25, Bearer 헤더에서
 * 전환). {@code rewardGranted} 는 요청의 {@code ref} 가 등록된 제휴 코드(예: 축제 사이트 FESTIVAL)이고
 * 이번 로그인으로 처음 지급됐을 때만 채워진다(2026-09-28). 그 외에는 {@code null}.
 */
public record KakaoLoginResponse(
        boolean isNewUser,
        String restoredResultId,
        RewardGranted rewardGranted
) {

    public record RewardGranted(String partnerName, int amount) {
    }
}
