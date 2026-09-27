package com.darkness.wks.signup;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.signup.dto.CreateSignupRequest;
import com.darkness.wks.signup.dto.PhotoUploadUrlRequest;
import com.darkness.wks.signup.dto.PhotoUploadUrlResponse;
import com.darkness.wks.signup.dto.ResendSignupRequest;
import com.darkness.wks.signup.dto.ResendSignupResponse;
import com.darkness.wks.signup.dto.SignupResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupControllerTest {

    @Mock
    private SignupService signupService;

    @Mock
    private PhotoUploadService photoUploadService;

    @Mock
    private SignupReapplyService reapplyService;

    @InjectMocks
    private SignupController signupController;

    @Test
    void createsSignup() {
        CreateSignupRequest request = new CreateSignupRequest("dev@dgu.ac.kr", null, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개", null);
        SignupResponse response = new SignupResponse(1024L, true, true, "신청이 접수됐다. 인증 메일을 확인해라.");
        when(signupService.createSignup(request)).thenReturn(response);

        SignupResponse body = signupController.createSignup(request).data();

        assertThat(body).isEqualTo(response);
    }

    @Test
    void createsPhotoUploadUrl() {
        PhotoUploadUrlRequest request = new PhotoUploadUrlRequest("image/jpeg");
        PhotoUploadUrlResponse response = new PhotoUploadUrlResponse("https://example.s3.amazonaws.com/signup-photos/a.jpg?X-Amz-...", "signup-photos/a.jpg", 600);
        when(photoUploadService.createUploadUrl("image/jpeg")).thenReturn(response);

        PhotoUploadUrlResponse body = signupController.createPhotoUploadUrl(request).data();

        assertThat(body).isEqualTo(response);
    }

    @Test
    void resendsVerificationMail() {
        ResendSignupRequest request = new ResendSignupRequest("dev@dgu.ac.kr");
        ResendSignupResponse response = ResendSignupResponse.of(true);
        when(signupService.resend("dev@dgu.ac.kr")).thenReturn(response);

        ResendSignupResponse body = signupController.resend(request).data();

        assertThat(body).isEqualTo(response);
    }

    @Test
    void returnsReapplyPrefill() {
        var prefill = new com.darkness.wks.signup.dto.SignupReapplyResponse("dev@dgu.ac.kr", null,
                "김동국", ContactMethod.PHONE, "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개");
        when(reapplyService.prefill("token")).thenReturn(prefill);

        assertThat(signupController.reapply("token").data()).isEqualTo(prefill);
    }

    @Test
    void redirectsToFrontendOnSuccessfulVerify() {
        ReflectionTestUtils.setField(signupController, "verifyRedirectUrl", "http://localhost:3000/verify");

        var result = signupController.verify("token");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(result.getHeaders().getLocation()).isEqualTo(java.net.URI.create("http://localhost:3000/verify"));
    }
}
