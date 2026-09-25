package com.darkness.wks.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                // 로그인 쿠키(HttpOnly)를 브라우저가 크로스 오리진 요청에 실어 보내려면 필요하다.
                // allowedOrigins 에 "*" 를 절대 안 쓰는 이유도 이것 — credentials 모드에서는 와일드카드가
                // 애초에 금지돼 있다 (CORS 스펙).
                .allowCredentials(true);
    }
}
