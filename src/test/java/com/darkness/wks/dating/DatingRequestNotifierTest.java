package com.darkness.wks.dating;

import com.darkness.wks.dating.DatingRequestNotifier.DatingRequestAcceptedEvent;
import com.darkness.wks.dating.DatingRequestNotifier.DatingRequestSentEvent;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatingRequestNotifierTest {

    private static final String EMAIL = "student@dgu.ac.kr";

    @Mock
    private JavaMailSender mailSender;

    private DatingRequestNotifier notifier() {
        return new DatingRequestNotifier(mailSender, new SyncTaskExecutor(), "noreply@wks.local");
    }

    @Test
    void sendsNotificationToRecipient() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        notifier().onRequestSent(new DatingRequestSentEvent(UUID.randomUUID(), EMAIL));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(EMAIL);
        assertThat(sent.getValue().getSubject()).contains("소개팅 신청");
    }

    @Test
    void sendsAcceptedNotificationToSenderWithoutContact() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        notifier().onRequestAccepted(new DatingRequestAcceptedEvent(UUID.randomUUID(), EMAIL));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(EMAIL);
        assertThat(sent.getValue().getSubject()).contains("수락");
        assertThat(sent.getValue().getContent().toString()).contains("보낸 신청함");
    }

    @Test
    void swallowsMailFailureBecauseRequestIsAlreadyCommitted() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> notifier().onRequestSent(new DatingRequestSentEvent(UUID.randomUUID(), EMAIL)))
                .doesNotThrowAnyException();
    }
}
