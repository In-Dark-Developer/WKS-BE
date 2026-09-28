package com.darkness.wks.common.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Clock;
import java.util.Properties;

/**
 * 메일 발송기를 직접 만든다. 이 빈이 있으면 Boot 의 기본 발송기는 만들어지지 않으므로(ConditionalOnMissingBean)
 * 기본 계정도 같은 spring.mail.* 설정으로 여기서 만든다. 보조 계정은 호스트·포트·SMTP 옵션을 기본 계정과
 * 같이 쓰고(둘 다 지메일) 계정·비밀번호·보내는 주소만 따로 받는다. 보조 계정 값이 비어 있으면 전환 없이
 * 기본 계정만 쓴다 — 지금까지와 같다.
 * <p>
 * spring.mail.host 가 없는 환경(테스트 등)에서는 Boot 와 같이 발송기를 만들지 않는다.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    public JavaMailSender mailSender(MailProperties properties,
                                     @Value("${app.mail.secondary.username:}") String secondaryUsername,
                                     @Value("${app.mail.secondary.password:}") String secondaryPassword,
                                     @Value("${app.mail.secondary.from:}") String secondaryFrom) {
        JavaMailSenderImpl primary = build(properties, properties.getUsername(), properties.getPassword());
        JavaMailSenderImpl secondary = secondaryUsername.isBlank() || secondaryPassword.isBlank()
                ? null : build(properties, secondaryUsername, secondaryPassword);
        return new FailoverMailSender(primary, secondary,
                secondaryFrom.isBlank() ? secondaryUsername : secondaryFrom, Clock.systemUTC());
    }

    // Boot 의 MailSenderPropertiesConfiguration 이 하던 설정을 그대로 옮긴다
    private static JavaMailSenderImpl build(MailProperties properties, String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        if (properties.getPort() != null) {
            sender.setPort(properties.getPort());
        }
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setProtocol(properties.getProtocol());
        if (properties.getDefaultEncoding() != null) {
            sender.setDefaultEncoding(properties.getDefaultEncoding().name());
        }
        Properties javaMailProperties = new Properties();
        javaMailProperties.putAll(properties.getProperties());
        sender.setJavaMailProperties(javaMailProperties);
        return sender;
    }
}
