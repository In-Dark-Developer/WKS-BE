package com.darkness.wks.common.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.timeout-seconds}")
    private int timeoutSeconds;

    @Bean
    public Client geminiClient() {
        // HttpOptions.timeout()는 밀리초 단위. google-genai 1.8.0+에서 OkHttp 전환 이슈로
        // 실제로 적용되지 않는 버전이 있었다는 이슈가 보고된 적 있으니, 실제 타임아웃 동작은
        // SDK 버전 업그레이드 시 재검증할 것 (https://github.com/googleapis/java-genai/issues/520)
        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(timeoutSeconds * 1000)
                .build();

        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(httpOptions)
                .build();
    }
}
