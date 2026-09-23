package com.darkness.wks.auth.dto;

/**
 * POST /api/auth/kakao 응답. api-spec.md §9.
 * {@code rewardGranted} 는 실(재화)·제휴 보상이 구현되기 전이라 항상 {@code null} 이다 — 필드 자체는
 * 미리 둬서 프론트가 나중에 값이 채워져도 계약을 다시 맞추지 않게 한다.
 */
public record KakaoLoginResponse(
        String token,
        boolean isNewUser,
        String restoredResultId,
        RewardGranted rewardGranted
) {

    public record RewardGranted(String partnerName, int amount) {
    }
}
