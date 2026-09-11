package com.darkness.wks.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
                        .description("축제 기간 한정 사주 계산·해석·공유·친구궁합 및 소개팅 사전등록(이메일 인증) API"));
    }
}
