package com.darkness.wks.signup;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.signup.dto.PhotoUploadUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoUploadService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    @Value("${app.aws.s3.photo-prefix}")
    private String photoPrefix;

    @Value("${app.aws.s3.presigned-url-ttl-minutes}")
    private long presignedUrlTtlMinutes;

    public PhotoUploadUrlResponse createUploadUrl(String contentType) {
        String extension = ALLOWED_CONTENT_TYPE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String key = photoPrefix + UUID.randomUUID() + "." + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        Duration ttl = Duration.ofMinutes(presignedUrlTtlMinutes);
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return new PhotoUploadUrlResponse(presignedRequest.url().toString(), key, (int) ttl.toSeconds());
    }

    // photoKey는 프론트가 직접 보내는 값이라, 실제로 S3에 업로드됐는지 여기서 확인한다
    public void verifyPhotoExists(String photoKey) {
        if (photoKey == null) {
            return;
        }
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(photoKey)
                    .build());
        } catch (NoSuchKeyException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
