package com.darkness.wks.dating;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.dto.DatingProfileRequest;
import com.darkness.wks.dating.dto.DatingProfileResponse;
import com.darkness.wks.dating.entity.DatingEmailVerification;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.result.ResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatingProfileServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final UUID PHOTO_ID = UUID.randomUUID();

    @Mock
    private DatingProfileRepository profileRepository;
    @Mock
    private ResultRepository resultRepository;
    @Mock
    private DatingPhotoService photoService;
    @Mock
    private DatingEmailVerificationService emailVerificationService;
    @Mock
    private DatingEmailCodeService emailCodeService;

    private DatingProfileService service() {
        DatingProfileService service = new DatingProfileService(profileRepository, resultRepository, photoService,
                emailVerificationService, emailCodeService);
        ReflectionTestUtils.setField(service, "allowedDomainsRaw", "dgu.ac.kr");
        return service;
    }

    private DatingProfileRequest request() {
        return request(null);
    }

    private DatingProfileRequest request(String reapplyToken) {
        return new DatingProfileRequest("student@dgu.ac.kr", "김동국", ContactMethod.PHONE,
                "010-1234-5678", "컴퓨터공학과", "INFP", "안녕하세요", PHOTO_ID, reapplyToken);
    }

    private DatingPhoto photo() {
        return new DatingPhoto(MEMBER_ID, "dating-photos/1/a.jpg");
    }

    private void stubProfileCreation() {
        when(resultRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(photoService.verifyOwnedPhoto(MEMBER_ID, PHOTO_ID)).thenReturn(photo());
        when(profileRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createWithCodeVerifiedEmailIsVerifiedWithoutSendingMail() {
        stubProfileCreation();
        when(emailCodeService.isVerified(MEMBER_ID, "student@dgu.ac.kr")).thenReturn(true);

        DatingProfileResponse response = service().create(MEMBER_ID, request());

        assertThat(response.emailVerified()).isTrue();
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void createWithoutCodeVerificationIsRejectedBeforeTouchingPhoto() {
        when(resultRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(emailCodeService.isVerified(MEMBER_ID, "student@dgu.ac.kr")).thenReturn(false);

        assertThatThrownBy(() -> service().create(MEMBER_ID, request()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DATING_NOT_VERIFIED));

        verifyNoInteractions(photoService);
        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendEmailCodeIssuesAndMailsCode() {
        Instant expiresAt = Instant.now().plusSeconds(600);
        when(emailCodeService.issue(MEMBER_ID, "student@dgu.ac.kr"))
                .thenReturn(new DatingEmailCodeService.IssuedCode("123456", expiresAt, Instant.now()));

        var response = service().sendEmailCode(MEMBER_ID, " Student@DGU.ac.kr ");

        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        verify(emailCodeService).sendCodeEmail("student@dgu.ac.kr", "123456");
    }

    @Test
    void sendEmailCodeDiscardsCodeAndReports503WhenMailFails() {
        when(emailCodeService.issue(MEMBER_ID, "student@dgu.ac.kr"))
                .thenReturn(new DatingEmailCodeService.IssuedCode("123456", Instant.now(), Instant.now()));
        doThrow(new DatingEmailCodeService.MailSendFailedException(new RuntimeException("smtp down")))
                .when(emailCodeService).sendCodeEmail(any(), any());

        assertThatThrownBy(() -> service().sendEmailCode(MEMBER_ID, "student@dgu.ac.kr"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MAIL_UNAVAILABLE));
        verify(emailCodeService).discard(MEMBER_ID);
    }

    @Test
    void sendEmailCodeRejectsNonSchoolEmailWithoutSending() {
        assertThatThrownBy(() -> service().sendEmailCode(MEMBER_ID, "someone@gmail.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_EMAIL_DOMAIN));
        verifyNoInteractions(emailCodeService);
    }

    @Test
    void sendEmailCodeRejectsMemberWhoAlreadyHasProfile() {
        when(profileRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service().sendEmailCode(MEMBER_ID, "student@dgu.ac.kr"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DATING_PROFILE_CONFLICT));
        verifyNoInteractions(emailCodeService);
    }

    @Test
    void createRejectsWhenNoLinkedResult() {
        when(resultRepository.existsByMemberId(MEMBER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service().create(MEMBER_ID, request()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESULT_NOT_FOUND));

        verifyNoInteractions(emailVerificationService, emailCodeService);
    }

    /** 재신청 초대는 학교메일 인증이 아니다 — 토큰을 실어 보내도 코드 인증 없이는 등록되지 않는다 */
    @Test
    void reapplyTokenDoesNotSkipCodeVerification() {
        when(resultRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(emailCodeService.isVerified(MEMBER_ID, "student@dgu.ac.kr")).thenReturn(false);

        assertThatThrownBy(() -> service().create(MEMBER_ID, request("invite")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DATING_NOT_VERIFIED));

        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void verifyEmailMarksProfileVerified() {
        DatingProfile profile = new DatingProfile(MEMBER_ID, "student@dgu.ac.kr", "김동국", ContactMethod.PHONE,
                "010-1234-5678", "컴퓨터공학과", "INFP", "안녕하세요", photo());
        DatingEmailVerification verification = new DatingEmailVerification("token", profile,
                Instant.now().plusSeconds(1800));
        when(emailVerificationService.verify("token")).thenReturn(verification);

        service().verifyEmail("token");

        assertThat(profile.isEligible()).isTrue();
    }
}
