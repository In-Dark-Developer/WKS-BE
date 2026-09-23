package com.darkness.wks.signup;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.signup.dto.PhotoUploadUrlResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoUploadServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private PhotoUploadService photoUploadService;

    @Test
    void createsUploadUrlWithPrefixAndExtension() throws Exception {
        ReflectionTestUtils.setField(photoUploadService, "bucket", "wks-photos");
        ReflectionTestUtils.setField(photoUploadService, "photoPrefix", "signup-photos/");
        ReflectionTestUtils.setField(photoUploadService, "presignedUrlTtlMinutes", 10L);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://wks-photos.s3.amazonaws.com/signup-photos/a.jpg").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        PhotoUploadUrlResponse response = photoUploadService.createUploadUrl("image/jpeg");

        assertThat(response.photoKey()).startsWith("signup-photos/").endsWith(".jpg");
        assertThat(response.expiresInSeconds()).isEqualTo(600);
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> photoUploadService.createUploadUrl("application/pdf"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void doesNothingWhenPhotoKeyIsNull() {
        photoUploadService.verifyPhotoExists(null);
    }

    @Test
    void rejectsMissingPhotoKey() {
        ReflectionTestUtils.setField(photoUploadService, "bucket", "wks-photos");
        doThrow(NoSuchKeyException.builder().build())
                .when(s3Client).headObject(any(HeadObjectRequest.class));

        assertThatThrownBy(() -> photoUploadService.verifyPhotoExists("signup-photos/missing.jpg"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
