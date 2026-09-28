package com.darkness.wks.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 기본 발송 계정이 실패하면 보조 계정으로 한 번 더 보낸다. 지메일 개인 계정은 하루 500통 한도라 축제 중
 * 인증 코드·알림 메일이 한도에 걸리면 인증이 막힌다. 호출부(JavaMailSender 를 주입받는 5곳)는 바꾸지 않는다.
 * <p>
 * 기본 계정이 한 번 실패하면 {@link #PRIMARY_COOLDOWN} 동안은 보조 계정으로 바로 보낸다 — 한도에 걸린 계정에
 * 매번 먼저 시도하면 메일마다 실패 왕복(수 초)이 붙는다. 보조 계정이 없으면 기본 계정 실패를 그대로 던진다.
 */
@Slf4j
public class FailoverMailSender implements JavaMailSender {

    static final Duration PRIMARY_COOLDOWN = Duration.ofHours(1);

    private final JavaMailSender primary;
    private final JavaMailSender secondary; // 설정 안 됐으면 null
    private final InternetAddress secondaryFrom;
    private final Clock clock;
    private volatile Instant primaryBlockedUntil = Instant.MIN;

    public FailoverMailSender(JavaMailSender primary, JavaMailSender secondary, String secondaryFrom, Clock clock) {
        this.primary = primary;
        this.secondary = secondary;
        this.secondaryFrom = toAddress(secondaryFrom);
        this.clock = clock;
    }

    @Override
    public MimeMessage createMimeMessage() {
        return primary.createMimeMessage();
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return primary.createMimeMessage(contentStream);
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        for (MimeMessage message : mimeMessages) {
            sendOne(message);
        }
    }

    // 코드에서는 쓰지 않지만 인터페이스 계약이라 같은 전환 규칙으로 보낸다
    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        for (SimpleMailMessage message : simpleMessages) {
            if (secondary == null || primaryAvailable()) {
                try {
                    primary.send(message);
                    return;
                } catch (MailException exception) {
                    if (secondary == null) {
                        throw exception;
                    }
                    blockPrimary(exception);
                }
            }
            SimpleMailMessage copy = new SimpleMailMessage(message);
            if (secondaryFrom != null) {
                copy.setFrom(secondaryFrom.toString());
            }
            secondary.send(copy);
        }
    }

    private void sendOne(MimeMessage message) {
        if (secondary == null || primaryAvailable()) {
            try {
                primary.send(message);
                return;
            } catch (MailException exception) {
                if (secondary == null) {
                    throw exception;
                }
                blockPrimary(exception);
            }
        }
        // 지메일은 인증한 계정과 다른 From 을 제 계정 주소로 바꿔 쓴다. 명시해 두면 받는 쪽 표시가 예측 가능하다
        if (secondaryFrom != null) {
            try {
                message.setFrom(secondaryFrom);
            } catch (MessagingException exception) {
                throw new MailPreparationException(exception);
            }
        }
        secondary.send(message);
    }

    private boolean primaryAvailable() {
        return !clock.instant().isBefore(primaryBlockedUntil);
    }

    private void blockPrimary(MailException exception) {
        primaryBlockedUntil = clock.instant().plus(PRIMARY_COOLDOWN);
        // 예외 메시지에는 수신 주소가 실릴 수 있어 타입만 남긴다 (AGENTS.md 로그 규칙)
        log.warn("primary mail account failed, switching to secondary for {}. cause={}",
                PRIMARY_COOLDOWN, exception.getClass().getSimpleName());
    }

    private static InternetAddress toAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new InternetAddress(value.trim());
        } catch (MessagingException exception) {
            throw new IllegalStateException("MAIL_SECONDARY_FROM 형식이 올바르지 않다", exception);
        }
    }
}
