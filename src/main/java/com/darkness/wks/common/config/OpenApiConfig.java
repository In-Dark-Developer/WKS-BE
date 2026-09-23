package com.darkness.wks.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("축제 사주 API")
                        .version("v1")
                        .description("축제 기간 한정 사주 계산·해석·공유·친구궁합 및 소개팅 사전등록(이메일 인증) API"))
                // GET /api/me 등 인증 필요 API 를 Swagger UI 우측 상단 Authorize 로 테스트할 수 있게 한다.
                // 실제 요청 인증은 common/auth/JwtAuthInterceptor 가 하고, 이건 문서·UI 용도뿐이다.
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
