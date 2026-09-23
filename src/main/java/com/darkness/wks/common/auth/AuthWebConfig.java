package com.darkness.wks.common.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 인증이 필요한 경로를 여기 한 곳에서 등록한다 — 나머지는 전부 익명이다. 소개팅(dating)·실(wallet) API 가
 * 생기면 패턴을 여기 추가한다. CORS 는 {@code common/config/WebConfig} 가 별도로 맡는다(WebMvcConfigurer
 * 는 여러 빈으로 나눠도 Spring 이 합쳐서 적용한다).
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthWebConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final CurrentMemberArgumentResolver currentMemberArgumentResolver;

    public AuthWebConfig(JwtAuthInterceptor jwtAuthInterceptor,
                          CurrentMemberArgumentResolver currentMemberArgumentResolver) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.currentMemberArgumentResolver = currentMemberArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor).addPathPatterns("/api/me/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentMemberArgumentResolver);
    }
}
