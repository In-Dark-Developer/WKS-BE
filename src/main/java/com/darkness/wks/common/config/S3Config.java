package com.darkness.wks.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@Configuration
public class S3Config {

    @Value("${app.aws.region}")
    private String region;

    // 자격증명은 명시적으로 주입하지 않는다. AWS 기본 자격증명 체인(AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY
    // 환경변수 또는 EC2 IAM 역할)이 알아서 찾는다 — 시크릿을 스프링 설정에 하드코딩하지 않기 위함
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(5))
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
