package com.darkness.wks.auth;

import com.darkness.wks.common.auth.AuthProperties;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String REDIRECT_URI = "https://threadoffate.site/auth/kakao/callback";

    private MockRestServiceServer server;
    private KakaoClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.createServer(builder);
        AuthProperties.Kakao kakaoConfig = new AuthProperties.Kakao(
                "client-id", "client-secret", TOKEN_URI, USER_INFO_URI, 2000, 3000);
        AuthProperties properties = new AuthProperties("", true, kakaoConfig, null);
        client = new KakaoClient(builder.build(), properties);
    }

    private MultiValueMap<String, String> expectedTokenForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", "client-id");
        form.add("redirect_uri", REDIRECT_URI);
        form.add("code", "auth-code");
        form.add("client_secret", "client-secret");
        return form;
    }

    @Test
    void 토큰_교환과_유저_조회를_거쳐_카카오_회원번호를_돌려준다() {
        setUp();
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expectedTokenForm()))
                .andRespond(withSuccess("""
                        {"token_type":"bearer","access_token":"kakao-access-token","expires_in":21599}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access-token"))
                .andRespond(withSuccess("""
                        {"id":123456789,"connected_at":"2026-09-22T00:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        long kakaoId = client.fetchKakaoId("auth-code", REDIRECT_URI);

        assertThat(kakaoId).isEqualTo(123456789L);
        server.verify();
    }

    @Test
    void invalid_grant_이면_INVALID_TOKEN_이다() {
        setUp();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().body("""
                        {"error":"invalid_grant","error_description":"authorization code not found"}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchKakaoId("expired-code", REDIRECT_URI))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    void invalid_client_이면_설정_오류로_보고_KAKAO_UNAVAILABLE_이다() {
        setUp();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().body("""
                        {"error":"invalid_client","error_description":"Bad client credentials"}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchKakaoId("auth-code", REDIRECT_URI))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KAKAO_UNAVAILABLE));
    }

    @Test
    void 카카오_서버_오류는_KAKAO_UNAVAILABLE_이다() {
        setUp();
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchKakaoId("auth-code", REDIRECT_URI))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KAKAO_UNAVAILABLE));
    }

    @Test
    void 유저_정보_조회가_실패하면_KAKAO_UNAVAILABLE_이다() {
        setUp();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("""
                        {"token_type":"bearer","access_token":"kakao-access-token","expires_in":21599}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchKakaoId("auth-code", REDIRECT_URI))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KAKAO_UNAVAILABLE));
    }

    @Test
    void 예외_메시지에_비밀값이_섞이지_않는다() {
        setUp();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().body("""
                        {"error":"invalid_client","error_description":"Bad client credentials"}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchKakaoId("auth-code", REDIRECT_URI))
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain("client-secret", "auth-code");
                });
    }
}
