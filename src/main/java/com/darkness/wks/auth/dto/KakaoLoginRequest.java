package com.darkness.wks.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * POST /api/auth/kakao 요청. api-spec.md §9.
 * {@code resultId}·{@code resultIds}·{@code ref} 는 형식이 틀리거나 존재하지 않아도 로그인 자체는 성공한다 — 여기서는
 * 검증하지 않고 서비스 계층이 조용히 무시한다(FR-AU-06·08). {@code resultIds} 는 이 브라우저가 로그인 전에 만든 결과들로,
 * 친구 보상 소급에만 쓴다(2026-09-30). 길이 초과도 거절하지 않고 앞에서부터 잘라 쓴다.
 */
public record KakaoLoginRequest(
        @NotBlank String code,
        @NotBlank String redirectUri,
        String resultId,
        String ref,
        List<String> resultIds
) {
}
