package com.darkness.wks.common.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCookieTest {

    private JwtCookie cookieFor(boolean secure) {
        AuthProperties properties = new AuthProperties(null, secure, null, new AuthProperties.Jwt("secret", 15));
        return new JwtCookie(properties);
    }

    @Test
    void 발급한_쿠키는_httpOnly_이고_ttl_일수만큼_유효하다() {
        ResponseCookie cookie = cookieFor(true).issue("jwt-value");

        assertThat(cookie.getName()).isEqualTo("wks_token");
        assertThat(cookie.getValue()).isEqualTo("jwt-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge().toDays()).isEqualTo(15);
        assertThat(cookie.getDomain()).isNull(); // host-only — dev/prod 쿠키가 안 섞인다
    }

    @Test
    void cookieSecure_false면_로컬처럼_Secure_속성을_뺀다() {
        ResponseCookie cookie = cookieFor(false).issue("jwt-value");

        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    void clear는_maxAge_0으로_삭제_쿠키를_만든다() {
        ResponseCookie cookie = cookieFor(true).clear();

        assertThat(cookie.getMaxAge().isZero()).isTrue();
        assertThat(cookie.getValue()).isEmpty();
    }

    @Test
    void readFrom은_요청의_쿠키에서_토큰값을_찾는다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("wks_token", "jwt-value"));

        assertThat(JwtCookie.readFrom(request)).isEqualTo("jwt-value");
    }

    @Test
    void readFrom은_쿠키가_없으면_null() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(JwtCookie.readFrom(request)).isNull();
    }
}
