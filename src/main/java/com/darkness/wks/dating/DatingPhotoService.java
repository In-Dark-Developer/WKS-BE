package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.dto.DatingPhotoUploadResponse;
import com.darkness.wks.dating.entity.DatingPhoto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class DatingPhotoService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final DatingPhotoRepository photoRepository;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    @Value("${app.aws.s3.presigned-url-ttl-minutes}")
    private long ttlMinutes;

    public DatingPhotoService(S3Presigner presigner, S3Client s3Client,
                              DatingPhotoRepository photoRepository) {
        this.presigner = presigner;
        this.s3Client = s3Client;
        this.photoRepository = photoRepository;
    }

    @Transactional
    public DatingPhotoUploadResponse createUploadUrl(Long memberId, String contentType) {
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String key = prefix(memberId) + UUID.randomUUID() + "." + extension;
        Duration ttl = Duration.ofMinutes(ttlMinutes);
        var request = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(key)
                        .contentType(contentType).build())
                .build();
        String url = presigner.presignPutObject(request).url().toString();
        DatingPhoto photo = photoRepository.save(new DatingPhoto(memberId, key));
        return new DatingPhotoUploadResponse(url, photo.getId(), (int) ttl.toSeconds());
    }

    public DatingPhoto verifyOwnedPhoto(Long memberId, UUID photoId) {
        DatingPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        if (!photo.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket)
                    .key(photo.getObjectKey()).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            throw exception;
        }
        return photo;
    }

    private static String prefix(Long memberId) {
        return "dating-photos/" + memberId + "/";
    }
}
