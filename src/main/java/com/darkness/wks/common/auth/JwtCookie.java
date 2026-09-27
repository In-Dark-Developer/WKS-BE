package com.darkness.wks.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 를 HttpOnly 쿠키로 주고받는다 (2026-09-25, Bearer 헤더에서 전환 — 사용자 결정).
 * {@code Domain} 속성을 일부러 안 준다 — host-only 쿠키가 돼서 운영(api.threadoffate.site)과
 * 개발(api-dev.threadoffate.site) 쿠키가 서로 안 섞인다. CSRF 는 SameSite=Lax 로 막는다
 * (상태 변경 API 는 전부 POST/PATCH 라 Lax 만으로 cross-site 요청이 자동 차단된다) — 별도 CSRF 토큰은
 * 두지 않는다.
 */
@Component
public class JwtCookie {

    static final String COOKIE_NAME = "wks_token";

    private final AuthProperties properties;

    public JwtCookie(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie issue(String token) {
        return build(token, Duration.ofDays(properties.jwt().ttlDays()));
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    static String readFrom(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
