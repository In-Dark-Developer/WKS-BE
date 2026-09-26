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
import com.darkness.wks.signup.SignupReapplyService;
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
    private SignupReapplyService reapplyService;

    private DatingProfileService service() {
        DatingProfileService service = new DatingProfileService(profileRepository, resultRepository, photoService,
                emailVerificationService, reapplyService);
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
    void createIssuesAndSendsVerificationMailAfterSavingProfile() {
        stubProfileCreation();
        when(emailVerificationService.issueToken(any()))
                .thenReturn(new DatingEmailVerification("token", null, Instant.now().plusSeconds(1800)));

        DatingProfileResponse response = service().create(MEMBER_ID, request());

        assertThat(response.emailVerified()).isFalse();
        verify(emailVerificationService).sendVerificationEmail("student@dgu.ac.kr", "token");
        verifyNoInteractions(reapplyService);
    }

    @Test
    void createStillSucceedsWhenVerificationMailFailsToSend() {
        stubProfileCreation();
        when(emailVerificationService.issueToken(any()))
                .thenReturn(new DatingEmailVerification("token", null, Instant.now().plusSeconds(1800)));
        doThrow(new DatingEmailVerificationService.MailSendFailedException(new RuntimeException("smtp down")))
                .when(emailVerificationService).sendVerificationEmail(any(), any());

        assertThat(service().create(MEMBER_ID, request())).isNotNull();
    }

    @Test
    void createRejectsWhenNoLinkedResult() {
        when(resultRepository.existsByMemberId(MEMBER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service().create(MEMBER_ID, request()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESULT_NOT_FOUND));

        verifyNoInteractions(emailVerificationService, reapplyService);
    }

    @Test
    void reapplyInviteMarksProfileVerifiedWithoutSendingMail() {
        stubProfileCreation();
        when(reapplyService.invitedEmail("invite")).thenReturn("student@dgu.ac.kr");

        DatingProfileResponse response = service().create(MEMBER_ID, request("invite"));

        assertThat(response.emailVerified()).isTrue();
        verify(reapplyService).consumeInvite("invite");
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void reapplyInviteForAnotherEmailIsRejected() {
        when(resultRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(reapplyService.invitedEmail("invite")).thenReturn("someone-else@dgu.ac.kr");

        assertThatThrownBy(() -> service().create(MEMBER_ID, request("invite")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(profileRepository, never()).saveAndFlush(any());
        verify(reapplyService, never()).consumeInvite(any());
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
