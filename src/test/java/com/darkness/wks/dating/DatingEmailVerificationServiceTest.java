package com.darkness.wks.dating;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.entity.DatingEmailVerification;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatingEmailVerificationServiceTest {

    @Mock
    private DatingEmailVerificationRepository verificationRepository;

    @Mock
    private JavaMailSender mailSender;

    private DatingEmailVerificationService service() {
        DatingEmailVerificationService service = new DatingEmailVerificationService(verificationRepository, mailSender);
        ReflectionTestUtils.setField(service, "ttlMinutes", 30L);
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@wks.local");
        ReflectionTestUtils.setField(service, "backendBaseUrl", "http://localhost:8080");
        return service;
    }

    private DatingProfile profile() {
        DatingPhoto photo = new DatingPhoto(1L, "dating-photos/1/a.jpg");
        return new DatingProfile(1L, "student@dgu.ac.kr", "김동국", ContactMethod.PHONE,
                "010-1234-5678", "컴퓨터공학과", "INFP", "안녕하세요", photo);
    }

    @Test
    void issuesTokenWithConfiguredTtl() {
        when(verificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DatingEmailVerification verification = service().issueToken(profile());

        assertThat(verification.getToken()).isNotBlank();
        assertThat(verification.getExpiresAt()).isAfter(Instant.now().plusSeconds(29 * 60));
        assertThat(verification.getExpiresAt()).isBefore(Instant.now().plusSeconds(31 * 60));
    }

    @Test
    void verifyMarksTokenUsedForValidToken() {
        DatingEmailVerification verification = new DatingEmailVerification("token", profile(),
                Instant.now().plusSeconds(1800));
        when(verificationRepository.findById("token")).thenReturn(Optional.of(verification));

        DatingEmailVerification result = service().verify("token");

        assertThat(result.getUsedAt()).isNotNull();
    }

    @Test
    void verifyRejectsUnknownToken() {
        when(verificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().verify("missing"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    void verifyRejectsExpiredToken() {
        DatingEmailVerification verification = new DatingEmailVerification("token", profile(),
                Instant.now().minusSeconds(1));
        when(verificationRepository.findById("token")).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> service().verify("token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    void verifyRejectsAlreadyUsedToken() {
        DatingEmailVerification verification = new DatingEmailVerification("token", profile(),
                Instant.now().plusSeconds(1800));
        verification.markUsed(Instant.now());
        when(verificationRepository.findById("token")).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> service().verify("token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    void sendVerificationEmailWrapsMailFailure() throws Exception {
        MimeMessage message = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(message);
        org.mockito.Mockito.doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        DatingEmailVerificationService service = service();

        assertThatThrownBy(() -> service.sendVerificationEmail("student@dgu.ac.kr", "token"))
                .isInstanceOf(DatingEmailVerificationService.MailSendFailedException.class);
    }
}
