package com.darkness.wks.auth;

import com.darkness.wks.auth.dto.KakaoLoginRequest;
import com.darkness.wks.auth.dto.KakaoLoginResponse;
import com.darkness.wks.common.auth.AuthProperties;
import com.darkness.wks.common.auth.JwtCookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 로그인·로그아웃 API 가 토큰을 응답 바디가 아니라 Set-Cookie 로 내려보내는지 확인한다
 * (2026-09-25, Bearer 헤더에서 쿠키로 전환). JwtCookie 는 실제 쿠키 속성을 검증해야 하므로 목이 아니라
 * 실제 인스턴스를 쓴다 — 그래서 @InjectMocks 대신 @BeforeEach 에서 직접 생성한다(필드 초기화식은
 * @Mock 주입보다 먼저 실행돼 authService 가 null 인 채로 잡힌다).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService,
                new JwtCookie(new AuthProperties(null, true, null, new AuthProperties.Jwt("secret", 15))));
    }

    @Test
    void 로그인_성공시_Set_Cookie로_토큰을_내려주고_바디에는_안_담는다() {
        KakaoLoginRequest request = new KakaoLoginRequest("code", "https://redirect", null, null);
        KakaoLoginResponse body = new KakaoLoginResponse(true, null, null);
        when(authService.login(request)).thenReturn(new AuthService.LoginOutcome("jwt-value", body));
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = authController.loginWithKakao(request, response);

        assertThat(result.data()).isEqualTo(body);
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("wks_token=jwt-value").contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void 로그아웃은_쿠키를_만료시킨다() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.logout(response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("wks_token=").contains("Max-Age=0");
    }
}
