package com.darkness.wks.auth;

import com.darkness.wks.common.auth.AuthProperties;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오 인가 코드를 액세스 토큰으로 교환하고, 그 토큰으로 회원번호(id)만 조회한다.
 * 동의항목을 받지 않으므로 다른 필드는 요청·파싱하지 않는다 (architecture.md §4 원칙 3).
 */
@Slf4j
@Component
public class KakaoClient {

    private final RestClient restClient;
    private final AuthProperties.Kakao config;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public KakaoClient(RestClient kakaoRestClient, AuthProperties properties) {
        this.restClient = kakaoRestClient;
        this.config = properties.kakao();
    }

    /** 인가 코드 → 액세스 토큰 교환 → 유저 정보 조회. 반환값은 카카오 회원번호(kakao_id)뿐이다 */
    public long fetchKakaoId(String code, String redirectUri) {
        String accessToken = exchangeToken(code, redirectUri);
        return fetchUserId(accessToken);
    }

    private String exchangeToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.clientId());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        form.add("client_secret", config.clientSecret());

        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(config.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                log.warn("kakao token response missing access_token");
                throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
            }
            return response.accessToken();
        } catch (RestClientResponseException e) {
            throw mapTokenError(e);
        } catch (ResourceAccessException e) {
            log.warn("kakao token request failed: connection/timeout");
            throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
        }
    }

    private long fetchUserId(String accessToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(config.userInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (response == null) {
                log.warn("kakao user info response empty");
                throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
            }
            return response.id();
        } catch (RestClientResponseException e) {
            log.warn("kakao user info request failed. status={}", e.getStatusCode());
            throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
        } catch (ResourceAccessException e) {
            log.warn("kakao user info request failed: connection/timeout");
            throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
        }
    }

    // 카카오 에러 코드(트러블슈팅 문서 기준): invalid_grant(KOE320 코드 만료·재사용, KOE303 redirect 불일치) 는
    // 클라이언트(프론트) 쪽 문제라 INVALID_TOKEN. invalid_client(KOE101·010, 키·시크릿 오류)·KOE006(미등록 redirect)은
    // 우리 설정 오류라 KAKAO_UNAVAILABLE + error 로 남긴다. 본문 전문은 로그에 남기지 않는다(NFR-AU-02).
    private BusinessException mapTokenError(RestClientResponseException e) {
        String kakaoError = parseKakaoError(e.getResponseBodyAsString());
        if ("invalid_grant".equals(kakaoError)) {
            log.warn("kakao token exchange rejected: invalid_grant");
            return new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        log.error("kakao token exchange failed. status={}, kakaoError={}", e.getStatusCode(), kakaoError);
        return new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
    }

    private String parseKakaoError(String body) {
        try {
            return mapper.readValue(body, KakaoErrorBody.class).error();
        } catch (Exception e) {
            // 본문이 JSON 이 아니거나 예상 모양이 아니면 코드 미상으로 본다 — 본문 자체는 로그에 남기지 않는다.
            return null;
        }
    }

    private record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record KakaoUserResponse(long id) {
    }

    private record KakaoErrorBody(String error, @JsonProperty("error_description") String errorDescription) {
    }
}
