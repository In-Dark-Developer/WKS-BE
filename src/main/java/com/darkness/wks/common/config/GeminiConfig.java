package com.darkness.wks.common.config;

import com.darkness.wks.saju.GeminiClientPool;
import com.google.genai.Client;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {

    @Bean
    public Client geminiClient(GeminiProperties properties) {
        String key = properties.activeFreeProjects().stream().findFirst()
                .map(GeminiProperties.Project::apiKey).orElse("not-configured");
        return GeminiClientPool.createClient(key, properties.timeoutSeconds());
    }

    @Bean
    public GeminiClientPool geminiClientPool(GeminiProperties properties, Client geminiClient) {
        return new GeminiClientPool(properties, geminiClient);
    }

}
