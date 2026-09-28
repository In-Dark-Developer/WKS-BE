package com.darkness.wks.common.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FailoverMailSenderTest {

    private final JavaMailSender primary = mock(JavaMailSender.class);
    private final JavaMailSender secondary = mock(JavaMailSender.class);

    private static MimeMessage message() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @Test
    void usesPrimaryWhileItWorks() {
        new FailoverMailSender(primary, secondary, "backup@gmail.com", Clock.systemUTC()).send(message());

        verify(primary).send(any(MimeMessage.class));
        verify(secondary, never()).send(any(MimeMessage.class));
    }

    @Test
    void fallsBackToSecondaryWithItsFromAddressWhenPrimaryFails() throws Exception {
        doThrow(new MailSendException("550 5.4.5 Daily user sending limit exceeded"))
                .when(primary).send(any(MimeMessage.class));
        MimeMessage message = message();
        message.setFrom("noreply@wks.local");

        new FailoverMailSender(primary, secondary, "backup@gmail.com", Clock.systemUTC()).send(message);

        verify(secondary).send(message);
        assertThat(message.getFrom()[0].toString()).isEqualTo("backup@gmail.com");
    }

    /** 한도에 걸린 기본 계정에 메일마다 먼저 시도하지 않는다 — 한 시간 동안은 보조 계정으로 바로 보낸다 */
    @Test
    void skipsPrimaryDuringCooldownThenRetriesIt() {
        doThrow(new MailSendException("limit")).when(primary).send(any(MimeMessage.class));
        Instant start = Instant.parse("2026-09-29T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        FailoverMailSender sender = new FailoverMailSender(primary, secondary, "backup@gmail.com", clock);

        sender.send(message());
        sender.send(message());
        verify(primary, times(1)).send(any(MimeMessage.class));
        verify(secondary, times(2)).send(any(MimeMessage.class));

        clock.now = start.plus(FailoverMailSender.PRIMARY_COOLDOWN);
        sender.send(message());
        verify(primary, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void withoutSecondaryPrimaryFailurePropagates() {
        doThrow(new MailSendException("down")).when(primary).send(any(MimeMessage.class));

        assertThatThrownBy(() -> new FailoverMailSender(primary, null, null, at(Instant.now())).send(message()))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    void configBuildsFailoverSenderFromSpringMailProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(MailConfig.class)
                .withPropertyValues("spring.mail.host=smtp.gmail.com", "spring.mail.port=587",
                        "spring.mail.username=main@gmail.com", "spring.mail.password=x",
                        "app.mail.secondary.username=backup@gmail.com", "app.mail.secondary.password=y")
                .run(context -> assertThat(context).getBean(JavaMailSender.class)
                        .isInstanceOf(FailoverMailSender.class));

        // 호스트가 없으면(테스트·로컬 최초 세팅) Boot 와 같이 발송기를 만들지 않는다
        new ApplicationContextRunner()
                .withUserConfiguration(MailConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(JavaMailSender.class));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
