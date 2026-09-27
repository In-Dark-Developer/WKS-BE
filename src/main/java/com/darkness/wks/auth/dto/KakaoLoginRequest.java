package com.darkness.wks.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/auth/kakao 요청. api-spec.md §9.
 * {@code resultId}·{@code ref} 는 형식이 틀리거나 존재하지 않아도 로그인 자체는 성공한다 — 여기서는
 * 검증하지 않고 서비스 계층이 조용히 무시한다(FR-AU-06·08).
 */
public record KakaoLoginRequest(
        @NotBlank String code,
        @NotBlank String redirectUri,
        String resultId,
        String ref
) {
}
