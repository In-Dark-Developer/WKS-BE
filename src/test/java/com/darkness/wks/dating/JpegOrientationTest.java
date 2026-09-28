package com.darkness.wks.dating;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class JpegOrientationTest {

    /** 가로로 저장된 200×100 JPEG. 왼쪽 절반 빨강, 오른쪽 절반 파랑 */
    private static byte[] landscapeJpeg() throws IOException {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.BLUE);
        g.fillRect(100, 0, 100, 100);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "JPEG", out);
        return out.toByteArray();
    }

    /** SOI 바로 뒤에 Orientation 하나만 든 EXIF(APP1) 세그먼트를 끼운다 — 폰 카메라가 만드는 구조와 같다 */
    private static byte[] withOrientation(byte[] jpeg, int orientation, boolean littleEndian) {
        byte[] tiff = littleEndian
                ? new byte[]{'I', 'I', 0x2A, 0, 8, 0, 0, 0,
                1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0, 0, 0, 0, 0}
                : new byte[]{'M', 'M', 0, 0x2A, 0, 0, 0, 8,
                0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, 0, 0, 0, 0};
        int length = 2 + 6 + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        out.writeBytes(tiff);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    @Test
    void readsOrientationInBothByteOrders() throws IOException {
        byte[] plain = landscapeJpeg();
        assertThat(JpegOrientation.read(plain)).isEqualTo(1);
        assertThat(JpegOrientation.read(withOrientation(plain, 6, false))).isEqualTo(6);
        assertThat(JpegOrientation.read(withOrientation(plain, 8, true))).isEqualTo(8);
    }

    @Test
    void brokenInputFallsBackToNoRotation() {
        assertThat(JpegOrientation.read(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x00}))
                .isEqualTo(1);
        assertThat(JpegOrientation.read("not a jpeg".getBytes())).isEqualTo(1);
    }

    /** 세로로 찍은 폰 사진(Orientation 6): 저장된 왼쪽(빨강)이 화면 위로 온다. 전에는 썸네일이 누워 좌우로 갈렸다 */
    @Test
    void blurredThumbnailFollowsExifRotation() throws IOException {
        byte[] rotated = withOrientation(landscapeJpeg(), 6, false);

        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(DatingPhotoService.blur(rotated, "JPEG")));

        assertThat(thumbnail.getWidth()).isEqualTo(96);
        assertThat(thumbnail.getHeight()).isEqualTo(121);
        // 누운 채였다면 왼쪽 위는 빨강, 오른쪽 위는 파랑으로 갈린다. 바로 섰으면 위쪽 전체가 빨강, 아래쪽 전체가 파랑
        for (int x : new int[]{5, 90}) {
            Color top = new Color(thumbnail.getRGB(x, 10));
            Color bottom = new Color(thumbnail.getRGB(x, 110));
            assertThat(top.getRed()).isGreaterThan(top.getBlue());
            assertThat(bottom.getBlue()).isGreaterThan(bottom.getRed());
        }
    }

    @Test
    void appliesCounterClockwiseRotation() {
        BufferedImage stored = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        stored.setRGB(0, 0, 0xFF0000);
        stored.setRGB(1, 0, 0x0000FF);

        BufferedImage shown = JpegOrientation.apply(stored, 8);

        // 반시계 90°: 저장된 오른쪽 끝이 화면 위로 올라간다
        assertThat(shown.getWidth()).isEqualTo(1);
        assertThat(shown.getHeight()).isEqualTo(2);
        assertThat(shown.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0x0000FF);
        assertThat(shown.getRGB(0, 1) & 0xFFFFFF).isEqualTo(0xFF0000);
    }
}
