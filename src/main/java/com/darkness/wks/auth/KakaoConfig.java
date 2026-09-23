package com.darkness.wks.auth;

import com.darkness.wks.common.auth.AuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class KakaoConfig {

    // 카카오 호출 전용 RestClient. 외부 호출은 전부 타임아웃을 명시한다(convention.md "타임아웃 없는 외부 호출" 규칙).
    // 새 HTTP 클라이언트 라이브러리를 추가하지 않기 위해 spring-web 내장 SimpleClientHttpRequestFactory 를 쓴다
    // (JDK HttpURLConnection 기반). 호출량이 적어(로그인 왕복 2회/요청) 충분하다.
    @Bean
    public RestClient kakaoRestClient(AuthProperties properties) {
        AuthProperties.Kakao kakao = properties.kakao();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(kakao.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(kakao.readTimeoutMs()));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
