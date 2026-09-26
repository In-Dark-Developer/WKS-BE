package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.entity.DatingEmailCode;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatingEmailCodeServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String EMAIL = "student@dgu.ac.kr";

    @Mock
    private DatingEmailCodeRepository codeRepository;
    @Mock
    private JavaMailSender mailSender;

    private DatingEmailCodeService service() {
        DatingEmailCodeService service = new DatingEmailCodeService(codeRepository, mailSender);
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@wks.local");
        return service;
    }

    /** 발급된 평문 코드를 알아야 verify 를 테스트할 수 있어서, 첫 발급 결과와 저장된 엔티티를 같이 돌려준다 */
    private record Issued(String code, DatingEmailCode entity) {
    }

    private Issued issueFresh() {
        ArgumentCaptor<DatingEmailCode> saved = ArgumentCaptor.forClass(DatingEmailCode.class);
        when(codeRepository.findForUpdate(MEMBER_ID)).thenReturn(Optional.empty());
        when(codeRepository.saveAndFlush(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        String code = service().issue(MEMBER_ID, EMAIL).code();
        return new Issued(code, saved.getValue());
    }

    private static DatingEmailCode sentAt(Instant sentAt) {
        return new DatingEmailCode(MEMBER_ID, EMAIL, "hash", sentAt.plus(DatingEmailCodeService.TTL), sentAt);
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void issuesSixDigitCodeAndStoresOnlyItsHash() {
        Issued issued = issueFresh();

        assertThat(issued.code()).matches("\\d{6}");
        assertThat(issued.entity().getCodeHash()).hasSize(64).doesNotContain(issued.code());
        assertThat(issued.entity().getExpiresAt()).isAfter(Instant.now().plusSeconds(9 * 60));
        assertThat(issued.entity().getSendCount()).isEqualTo(1);
    }

    @Test
    void resendWithinCooldownIsRejected() {
        when(codeRepository.findForUpdate(MEMBER_ID)).thenReturn(Optional.of(sentAt(Instant.now())));

        assertErrorCode(catchThrowable(() -> service().issue(MEMBER_ID, EMAIL)), ErrorCode.EMAIL_CODE_RATE_LIMITED);
    }

    @Test
    void resendAfterCooldownReplacesCodeAndResetsVerification() {
        DatingEmailCode existing = sentAt(Instant.now().minusSeconds(120));
        existing.markVerified(Instant.now().minusSeconds(100));
        existing.recordFailure();
        when(codeRepository.findForUpdate(MEMBER_ID)).thenReturn(Optional.of(existing));

        service().issue(MEMBER_ID, "other@dgu.ac.kr");

        assertThat(existing.getEmail()).isEqualTo("other@dgu.ac.kr");
        assertThat(existing.getVerifiedAt()).isNull();
        assertThat(existing.getFailedAttempts()).isZero();
        assertThat(existing.getSendCount()).isEqualTo(2);
    }

    @Test
    void sendLimitPerWindowIsEnforcedUntilWindowPasses() {
        Instant windowStart = Instant.now().minusSeconds(3600);
        DatingEmailCode existing = sentAt(windowStart);
        for (int i = 1; i < DatingEmailCodeService.MAX_SENDS_PER_WINDOW; i++) {
            existing.reissue(EMAIL, "hash", Instant.now(), windowStart, false);
        }
        when(codeRepository.findForUpdate(MEMBER_ID)).thenReturn(Optional.of(existing));

        assertErrorCode(catchThrowable(() -> service().issue(MEMBER_ID, EMAIL)), ErrorCode.EMAIL_CODE_RATE_LIMITED);

        ReflectionTestUtils.setField(existing, "sendWindowStart", Instant.now().minus(DatingEmailCodeService.SEND_WINDOW));
        service().issue(MEMBER_ID, EMAIL);
        assertThat(existing.getSendCount()).isEqualTo(1);
    }

    @Test
    void correctCodeVerifiesEmail() {
        Issued issued = issueFresh();
        when(codeRepository.findForUpdate(MEMBER_ID)).thenReturn(Optional.of(issued.entity()));

        service().verify(MEMBER_ID, EMAIL, issued.code());

        assertThat(issued.entity().isVerifiedFor(EMAIL)).isTrue();
    }

    @Test
    void wrongCodeCountsFailureAndLocksAfterMaxAttempts() {
        Issued issued = issueFresh();
        when(codeRepository.findForUpdate(MEMBER_ID)).thenReturn(Optional.of(issued.entity()));
        String wrong = issued.code().equals("000000") ? "111111" : "000000";

        for (int i = 0; i < DatingEmailCodeService.MAX_FAILED_ATTEMPTS; i++) {
            assertErrorCode(catchThrowable(() -> service().verify(MEMBER_ID, EMAIL, wrong)), ErrorCode.INVALID_EMAIL_CODE);
        }
        assertThat(issued.entity().getFailedAttempts()).isEqualTo(DatingEmailCodeService.MAX_FAILED_ATTEMPTS);

        // 한도를 넘기면 맞는 코드도 받지 않는다 — 새로 발송받아야 한다
        assertErrorCode(catchThrowable(() -> service().verify(MEMBER_ID, EMAIL, issued.code())),
                ErrorCode.INVALID_EMAIL_CODE);
        assertThat(issued.entity().getVerifiedAt()).isNull();
    }

    @Test
    void codeForAnotherEmailOrExpiredCodeIsRejected() {
        Issued issued = issueFresh();
        when(codeRepository.findForUpdate(MEMBER_ID)).thenReturn(Optional.of(issued.entity()));

        assertErrorCode(catchThrowable(() -> service().verify(MEMBER_ID, "other@dgu.ac.kr", issued.code())),
                ErrorCode.INVALID_EMAIL_CODE);

        ReflectionTestUtils.setField(issued.entity(), "expiresAt", Instant.now().minusSeconds(1));
        assertErrorCode(catchThrowable(() -> service().verify(MEMBER_ID, EMAIL, issued.code())),
                ErrorCode.INVALID_EMAIL_CODE);
    }

    @Test
    void verifyWithoutIssuedCodeIsRejected() {
        when(codeRepository.findForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        assertErrorCode(catchThrowable(() -> service().verify(MEMBER_ID, EMAIL, "123456")),
                ErrorCode.INVALID_EMAIL_CODE);
    }

    @Test
    void isVerifiedOnlyForTheVerifiedEmail() {
        DatingEmailCode code = sentAt(Instant.now());
        code.markVerified(Instant.now());
        when(codeRepository.findById(MEMBER_ID)).thenReturn(Optional.of(code));

        assertThat(service().isVerified(MEMBER_ID, EMAIL)).isTrue();
        assertThat(service().isVerified(MEMBER_ID, "other@dgu.ac.kr")).isFalse();
    }

    @Test
    void sendsCodeInMailBody() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        service().sendCodeEmail(EMAIL, "123456");

        verify(mailSender).send(message);
        assertThat((String) message.getContent()).contains("123456");
    }

    @Test
    void wrapsMailFailure() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service().sendCodeEmail(EMAIL, "123456"))
                .isInstanceOf(DatingEmailCodeService.MailSendFailedException.class);
        verify(codeRepository, never()).deleteById(any());
    }
}
