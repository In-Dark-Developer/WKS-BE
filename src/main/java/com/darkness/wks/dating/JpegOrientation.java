package com.darkness.wks.dating;

import java.awt.image.BufferedImage;

/**
 * JPEG EXIF 의 Orientation(0x0112) 값을 읽고 그대로 적용한다. 값 하나 읽자고 EXIF 라이브러리를 들이지 않으려고
 * 필요한 부분(APP1 → TIFF 헤더 → IFD0)만 직접 읽는다. 형식이 조금이라도 어긋나면 1(회전 없음)로 본다 —
 * 썸네일이 누운 채로 나가는 편이 등록 실패보다 낫다.
 */
final class JpegOrientation {

    private static final int TAG_ORIENTATION = 0x0112;

    private JpegOrientation() {
    }

    /** 5~8 은 90° 회전이 섞여 있어 가로·세로가 바뀐다 */
    static boolean swapsAxes(int orientation) {
        return orientation >= 5 && orientation <= 8;
    }

    static int read(byte[] jpeg) {
        try {
            if (u8(jpeg, 0) != 0xFF || u8(jpeg, 1) != 0xD8) {
                return 1;
            }
            int pos = 2;
            while (pos + 4 <= jpeg.length) {
                if (u8(jpeg, pos) != 0xFF) {
                    return 1;
                }
                int marker = u8(jpeg, pos + 1);
                if (marker == 0xDA || marker == 0xD9) { // 이미지 데이터 시작·끝 — EXIF 는 그 앞에만 있다
                    return 1;
                }
                int length = (u8(jpeg, pos + 2) << 8) | u8(jpeg, pos + 3);
                int body = pos + 4;
                if (marker == 0xE1 && length >= 8 && isExifHeader(jpeg, body)) {
                    return readFromTiff(jpeg, body + 6, body + length - 2);
                }
                pos += 2 + length;
            }
            return 1;
        } catch (IndexOutOfBoundsException exception) {
            return 1;
        }
    }

    private static boolean isExifHeader(byte[] b, int at) {
        return b[at] == 'E' && b[at + 1] == 'x' && b[at + 2] == 'i' && b[at + 3] == 'f'
                && b[at + 4] == 0 && b[at + 5] == 0;
    }

    private static int readFromTiff(byte[] b, int tiff, int end) {
        boolean little;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
            little = true;
        } else if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        int ifd = tiff + (int) u32(b, tiff + 4, little);
        int count = u16(b, ifd, little);
        for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(b, entry, little) == TAG_ORIENTATION) {
                int value = u16(b, entry + 8, little); // SHORT 는 값 칸의 앞 2바이트에 들어 있다
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    /**
     * 저장된 픽셀에 EXIF 방향을 적용해 화면에 보이는 방향의 이미지를 만든다. 작은 썸네일에만 쓰므로
     * 픽셀 단위로 옮긴다. 각 식은 "보이는 좌표 (x, y) 가 저장된 이미지의 어느 좌표인가"다.
     */
    static BufferedImage apply(BufferedImage stored, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return stored;
        }
        int w = stored.getWidth();
        int h = stored.getHeight();
        boolean swap = swapsAxes(orientation);
        BufferedImage shown = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < shown.getHeight(); y++) {
            for (int x = 0; x < shown.getWidth(); x++) {
                int sx;
                int sy;
                switch (orientation) {
                    case 2 -> { sx = w - 1 - x; sy = y; }          // 좌우 반전
                    case 3 -> { sx = w - 1 - x; sy = h - 1 - y; }  // 180°
                    case 4 -> { sx = x; sy = h - 1 - y; }          // 상하 반전
                    case 5 -> { sx = y; sy = x; }                  // 전치
                    case 6 -> { sx = y; sy = h - 1 - x; }          // 시계 방향 90° (세로로 찍은 폰 사진 대부분)
                    case 7 -> { sx = w - 1 - y; sy = h - 1 - x; }  // 반대 전치
                    default -> { sx = w - 1 - y; sy = x; }         // 8: 반시계 방향 90°
                }
                shown.setRGB(x, y, stored.getRGB(sx, sy));
            }
        }
        return shown;
    }

    private static int u8(byte[] b, int at) {
        return b[at] & 0xFF;
    }

    private static int u16(byte[] b, int at, boolean little) {
        return little ? u8(b, at) | (u8(b, at + 1) << 8) : (u8(b, at) << 8) | u8(b, at + 1);
    }

    private static long u32(byte[] b, int at, boolean little) {
        return little
                ? u8(b, at) | (u8(b, at + 1) << 8) | (u8(b, at + 2) << 16) | ((long) u8(b, at + 3) << 24)
                : ((long) u8(b, at) << 24) | (u8(b, at + 1) << 16) | (u8(b, at + 2) << 8) | u8(b, at + 3);
    }
}
