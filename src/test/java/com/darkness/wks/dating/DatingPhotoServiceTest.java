package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.entity.DatingPhoto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatingPhotoServiceTest {

    @Test
    void signsOnlyIndependentThumbnailKey() throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        DatingPhotoService service = new DatingPhotoService(presigner, mock(S3Client.class),
                mock(DatingPhotoRepository.class));
        ReflectionTestUtils.setField(service, "bucket", "private-photos");
        ReflectionTestUtils.setField(service, "ttlMinutes", 10L);
        DatingPhoto photo = new DatingPhoto(42L, "dating-photos/42/original-secret.jpg");
        PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://example.com/blurred.png").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);

        assertThat(service.thumbnailUrl(photo)).isEqualTo("https://example.com/blurred.png");
        verify(presigner).presignGetObject(argThat((GetObjectPresignRequest request) ->
                request.getObjectRequest().key().equals("dating-thumbnails/" + photo.getId() + ".png")
                        && !request.getObjectRequest().key().contains("original-secret")));
    }

    @Test
    void rejectsWebpBeforeIssuingUploadUrl() {
        DatingPhotoService service = new DatingPhotoService(mock(S3Presigner.class), mock(S3Client.class),
                mock(DatingPhotoRepository.class));
        assertThatThrownBy(() -> service.createUploadUrl(42L, "image/webp"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void acceptsJpegAndPngAndProducesSmallBlurredPng() throws Exception {
        for (String format : new String[] {"JPEG", "PNG"}) {
            BufferedImage original = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 120; y++) {
                for (int x = 0; x < 120; x++) {
                    original.setRGB(x, y, x < 60 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(original, format, bytes);

            BufferedImage blurred = ImageIO.read(new java.io.ByteArrayInputStream(
                    DatingPhotoService.blur(bytes.toByteArray(), format)));

            assertThat(blurred.getWidth()).isEqualTo(96);
            assertThat(blurred.getHeight()).isEqualTo(121);
            assertThat(blurred.getRGB(48, 60) & 255).isBetween(1, 254);
        }
    }

    @Test
    void rejectsDisguisedOrInvalidImage() throws Exception {
        BufferedImage original = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(original, "PNG", bytes);

        assertThatThrownBy(() -> DatingPhotoService.blur(bytes.toByteArray(), "JPEG"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> DatingPhotoService.blur("not an image".getBytes(), "PNG"))
                .isInstanceOf(BusinessException.class);
    }
}
