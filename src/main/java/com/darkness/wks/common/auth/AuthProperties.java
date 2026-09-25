package com.darkness.wks.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.auth.* 설정. 값이 비어 있어도(로컬 최초 세팅 등) 기동은 그대로 되고, 로그인 API만
 * {@link com.darkness.wks.common.exception.ErrorCode#KAKAO_UNAVAILABLE} 로 막힌다 (AuthWebConfig 참고).
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        // 콤마 구분 원문 그대로 받는다 — SignupService.allowedEmailDomainsRaw 와 같은 패턴.
        // RedirectUriPolicy 가 파싱한다(빈 값이면 화이트리스트가 없다는 뜻 — 어떤 redirectUri 도 거부).
        String allowedRedirectUris,
        // 로컬(plain HTTP)에서만 false. 브라우저가 Secure 쿠키를 HTTPS 가 아닌 연결에는 저장하지 않는다.
        boolean cookieSecure,
        Kakao kakao,
        Jwt jwt
) {

    public record Kakao(
            String clientId,
            String clientSecret,
            String tokenUri,
            String userInfoUri,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
    }

    public record Jwt(
            String secret,
            int ttlDays
    ) {
    }

    /** 카카오 client-id/secret, JWT 서명키 중 하나라도 비어 있으면 로그인 기능을 쓸 수 없다. */
    public boolean isConfigured() {
        return kakao != null && jwt != null
                && notBlank(kakao.clientId()) && notBlank(kakao.clientSecret())
                && notBlank(jwt.secret());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
