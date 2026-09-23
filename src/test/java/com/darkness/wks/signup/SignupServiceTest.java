package com.darkness.wks.signup;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.signup.dto.CreateSignupRequest;
import com.darkness.wks.signup.dto.ResendSignupResponse;
import com.darkness.wks.signup.dto.SignupResponse;
import com.darkness.wks.signup.entity.EmailVerification;
import com.darkness.wks.signup.entity.Signup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    private SignupRepository signupRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private SignupService signupService;

    @Test
    void createsSignupWithoutResult() {
        when(signupRepository.existsByEmail("dev@dgu.ac.kr")).thenReturn(false);
        when(signupRepository.save(any())).thenAnswer(invocation -> {
            Signup signup = invocation.getArgument(0);
            ReflectionTestUtils.setField(signup, "id", 1024L);
            return signup;
        });
        Signup savedSignup = new Signup("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개");
        when(emailVerificationService.issueToken(any()))
                .thenReturn(new EmailVerification("token", savedSignup, Instant.now().plusSeconds(1800)));

        SignupResponse response = signupService.createSignup(
                new CreateSignupRequest("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개"));

        assertThat(response.signupId()).isEqualTo(1024L);
        assertThat(response.couponIssued()).isTrue();
        assertThat(response.mailSent()).isTrue();
        verifyNoInteractions(resultRepository);
    }

    @Test
    void marksMailSentFalseWhenSendFails() {
        when(signupRepository.existsByEmail(anyString())).thenReturn(false);
        when(signupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Signup savedSignup = new Signup("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개");
        when(emailVerificationService.issueToken(any()))
                .thenReturn(new EmailVerification("token", savedSignup, Instant.now().plusSeconds(1800)));
        doThrow(new EmailVerificationService.MailSendFailedException(new RuntimeException("smtp down")))
                .when(emailVerificationService).sendVerificationEmail(anyString(), anyString());

        SignupResponse response = signupService.createSignup(
                new CreateSignupRequest("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개"));

        assertThat(response.mailSent()).isFalse();
    }

    @Test
    void rejectsDuplicateEmail() {
        when(signupRepository.existsByEmail("dev@dgu.ac.kr")).thenReturn(true);

        assertThatThrownBy(() -> signupService.createSignup(
                new CreateSignupRequest("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_SIGNUP));

        verify(signupRepository, never()).save(any());
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void rejectsDisallowedEmailDomainWhenWhitelistConfigured() {
        ReflectionTestUtils.setField(signupService, "allowedEmailDomainsRaw", "dgu.ac.kr");

        assertThatThrownBy(() -> signupService.createSignup(
                new CreateSignupRequest("dev@gmail.com", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_EMAIL_DOMAIN));

        verifyNoInteractions(signupRepository, emailVerificationService);
    }

    @Test
    void allowsAnyDomainWhenWhitelistNotConfigured() {
        when(signupRepository.existsByEmail(anyString())).thenReturn(false);
        when(signupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Signup savedSignup = new Signup("dev@gmail.com", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개");
        when(emailVerificationService.issueToken(any()))
                .thenReturn(new EmailVerification("token", savedSignup, Instant.now().plusSeconds(1800)));

        SignupResponse response = signupService.createSignup(
                new CreateSignupRequest("dev@gmail.com", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개"));

        assertThat(response.couponIssued()).isTrue();
    }

    @Test
    void createsSignupWithAllOptionalProfileFieldsOmitted() {
        when(signupRepository.existsByEmail("dev@dgu.ac.kr")).thenReturn(false);
        when(signupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Signup savedSignup = new Signup("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE,
                null, null, null, null, null, null);
        when(emailVerificationService.issueToken(any()))
                .thenReturn(new EmailVerification("token", savedSignup, Instant.now().plusSeconds(1800)));

        SignupResponse response = signupService.createSignup(
                new CreateSignupRequest("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE,
                        null, null, null, null, null, null));

        assertThat(response.couponIssued()).isTrue();
    }

    @Test
    void rejectsMalformedPhoneContact() {
        when(signupRepository.existsByEmail(anyString())).thenReturn(false);

        assertThatThrownBy(() -> signupService.createSignup(
                new CreateSignupRequest("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE,
                        "김동국", ContactMethod.PHONE, "not-a-phone-number", "컴퓨터공학과", "INFP", "자기소개")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(signupRepository, never()).save(any());
    }

    @Test
    void allowsInstagramContactWithoutPhoneFormat() {
        when(signupRepository.existsByEmail(anyString())).thenReturn(false);
        when(signupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Signup savedSignup = new Signup("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE,
                "김동국", ContactMethod.INSTAGRAM, "my_ig_handle", "컴퓨터공학과", "INFP", "자기소개");
        when(emailVerificationService.issueToken(any()))
                .thenReturn(new EmailVerification("token", savedSignup, Instant.now().plusSeconds(1800)));

        SignupResponse response = signupService.createSignup(
                new CreateSignupRequest("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE,
                        "김동국", ContactMethod.INSTAGRAM, "my_ig_handle", "컴퓨터공학과", "INFP", "자기소개"));

        assertThat(response.couponIssued()).isTrue();
    }

    @Test
    void rejectsMissingResultForProvidedResultId() {
        UUID resultId = UUID.randomUUID();
        when(signupRepository.existsByEmail(anyString())).thenReturn(false);
        when(resultRepository.findById(resultId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> signupService.createSignup(
                new CreateSignupRequest("dev@dgu.ac.kr", resultId.toString(), Gender.MALE, Gender.FEMALE,
                        "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESULT_NOT_FOUND));

        verify(signupRepository, never()).save(any());
    }

    @Test
    void createsSignupWithExistingResult() {
        Result result = new Result("서연", LocalDate.of(2002, 3, 14), null, null, Gender.FEMALE,
                "임오", "계묘", "갑진", "신미");
        UUID resultId = UUID.randomUUID();
        ReflectionTestUtils.setField(result, "id", resultId);
        when(signupRepository.existsByEmail(anyString())).thenReturn(false);
        when(resultRepository.findById(resultId)).thenReturn(Optional.of(result));
        when(signupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Signup savedSignup = new Signup("dev@dgu.ac.kr", result, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개");
        when(emailVerificationService.issueToken(any()))
                .thenReturn(new EmailVerification("token", savedSignup, Instant.now().plusSeconds(1800)));

        SignupResponse response = signupService.createSignup(
                new CreateSignupRequest("dev@dgu.ac.kr", resultId.toString(), Gender.MALE, Gender.FEMALE,
                        "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개"));

        assertThat(response.couponIssued()).isTrue();
    }

    @Test
    void resendRejectsUnknownEmail() {
        when(signupRepository.findByEmail("dev@dgu.ac.kr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> signupService.resend("dev@dgu.ac.kr"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void resendRejectsAlreadyVerifiedEmail() {
        Signup signup = new Signup("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개");
        signup.markVerified(Instant.now());
        when(signupRepository.findByEmail("dev@dgu.ac.kr")).thenReturn(Optional.of(signup));

        assertThatThrownBy(() -> signupService.resend("dev@dgu.ac.kr"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void resendIssuesNewTokenForUnverifiedEmail() {
        Signup signup = new Signup("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개");
        when(signupRepository.findByEmail("dev@dgu.ac.kr")).thenReturn(Optional.of(signup));
        when(emailVerificationService.issueToken(signup))
                .thenReturn(new EmailVerification("token2", signup, Instant.now().plusSeconds(1800)));

        ResendSignupResponse response = signupService.resend("dev@dgu.ac.kr");

        assertThat(response.mailSent()).isTrue();
        verify(emailVerificationService).sendVerificationEmail("dev@dgu.ac.kr", "token2");
    }

    @Test
    void verifyEmailMarksSignupVerified() {
        Signup signup = new Signup("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개");
        EmailVerification verification = new EmailVerification("token", signup, Instant.now().plusSeconds(1800));
        when(emailVerificationService.verify("token")).thenReturn(verification);

        signupService.verifyEmail("token");

        assertThat(signup.isVerified()).isTrue();
    }
}
