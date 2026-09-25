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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class DatingPhotoService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png");
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_PIXELS = 20_000_000;
    private static final int THUMBNAIL_WIDTH = 96;
    private static final int THUMBNAIL_HEIGHT = 121;
    private static final double BLUR_SIGMA = 15.0 * THUMBNAIL_WIDTH / 343.0;

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

    public void createBlurredThumbnail(DatingPhoto photo) {
        byte[] original;
        try (var input = s3Client.getObject(GetObjectRequest.builder().bucket(bucket)
                .key(photo.getObjectKey()).build())) {
            original = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (original.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        byte[] thumbnail = blur(original, photo.getObjectKey().endsWith(".jpg") ? "JPEG" : "PNG");
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(thumbnailKey(photo))
                        .contentType("image/png").build(), RequestBody.fromBytes(thumbnail));
    }

    public String thumbnailUrl(DatingPhoto photo) {
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(ttlMinutes))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(thumbnailKey(photo)).build())
                .build();
        return presigner.presignGetObject(request).url().toString();
    }

    static byte[] blur(byte[] original, String expectedFormat) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase(expectedFormat)
                        || (long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
                BufferedImage source = reader.read(0);
                BufferedImage small = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = small.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    double targetRatio = (double) THUMBNAIL_WIDTH / THUMBNAIL_HEIGHT;
                    int cropWidth = Math.min(source.getWidth(), (int) Math.round(source.getHeight() * targetRatio));
                    int cropHeight = Math.min(source.getHeight(), (int) Math.round(source.getWidth() / targetRatio));
                    int left = (source.getWidth() - cropWidth) / 2;
                    int top = (source.getHeight() - cropHeight) / 2;
                    graphics.drawImage(source, 0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                            left, top, left + cropWidth, top + cropHeight, null);
                } finally {
                    graphics.dispose();
                }
                BufferedImage blurred = gaussianBlur(small);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(blurred, "PNG", output);
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static BufferedImage gaussianBlur(BufferedImage source) {
        int radius = (int) Math.ceil(BLUR_SIGMA * 3);
        double[] weights = new double[radius * 2 + 1];
        double sum = 0;
        for (int offset = -radius; offset <= radius; offset++) {
            double weight = Math.exp(-(offset * offset) / (2 * BLUR_SIGMA * BLUR_SIGMA));
            weights[offset + radius] = weight;
            sum += weight;
        }
        for (int index = 0; index < weights.length; index++) {
            weights[index] /= sum;
        }
        return blurAxis(blurAxis(source, weights, true), weights, false);
    }

    private static BufferedImage blurAxis(BufferedImage source, double[] weights, boolean horizontal) {
        BufferedImage target = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        int radius = weights.length / 2;
        for (int y = 0; y < THUMBNAIL_HEIGHT; y++) {
            for (int x = 0; x < THUMBNAIL_WIDTH; x++) {
                double red = 0;
                double green = 0;
                double blue = 0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int sampleX = horizontal ? Math.max(0, Math.min(THUMBNAIL_WIDTH - 1, x + offset)) : x;
                    int sampleY = horizontal ? y : Math.max(0, Math.min(THUMBNAIL_HEIGHT - 1, y + offset));
                    int rgb = source.getRGB(sampleX, sampleY);
                    double weight = weights[offset + radius];
                    red += ((rgb >> 16) & 255) * weight;
                    green += ((rgb >> 8) & 255) * weight;
                    blue += (rgb & 255) * weight;
                }
                target.setRGB(x, y, (((int) Math.round(red)) << 16)
                        | (((int) Math.round(green)) << 8) | ((int) Math.round(blue)));
            }
        }
        return target;
    }

    private static String thumbnailKey(DatingPhoto photo) {
        return "dating-thumbnails/" + photo.getId() + ".png";
    }

    private static String prefix(Long memberId) {
        return "dating-photos/" + memberId + "/";
    }
}
