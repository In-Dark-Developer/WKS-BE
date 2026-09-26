package com.darkness.wks.dating;

import com.darkness.wks.common.auth.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 소개팅 경로에도 기존 로그인 쿠키(JWT) 검증을 적용한다.
 * 학교메일 인증 매직링크(/profile/verify)는 토큰 자체가 신원 증명이라 로그인 쿠키 없이도 눌러야 해서 제외한다
 * (signup/의 /api/signups/verify 와 같은 이유).
 */
@Configuration
public class DatingAuthWebConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    public DatingAuthWebConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/dating/**")
                .excludePathPatterns("/api/dating/profile/verify");
    }
}
