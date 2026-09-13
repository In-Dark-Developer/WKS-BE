package com.darkness.wks.saju;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 운명 제목 8종. 결혼·자녀·연애 각각 상(SS/S/A+)·하(A/B+/B) 조합 2×2×2 (기획 2026-09-13).
 * 제목은 {@code resources/destiny-titles.txt} ("결혼자녀연애=제목", 예: 상하상=…). 저장하지 않고 점수로 계산한다.
 */
public final class DestinyTitle {

    private static final Map<String, String> TITLES = load();

    private DestinyTitle() {
    }

    public static String of(int marriageScore, int childrenScore, int loveScore) {
        return TITLES.get(level(marriageScore) + level(childrenScore) + level(loveScore));
    }

    /** 상 = A+ 이상 */
    static String level(int score) {
        return Grade.of(score).ordinal() <= Grade.A_PLUS.ordinal() ? "상" : "하";
    }

    private static Map<String, String> load() {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = DestinyTitle.class.getClassLoader().getResourceAsStream("destiny-titles.txt")) {
            if (in == null) throw new IllegalStateException("resource not found: destiny-titles.txt");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv = line.split("=", 2);
                map.put(kv[0].strip(), kv[1].strip());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (map.size() != 8) throw new IllegalStateException("destiny-titles.txt 는 8줄이어야 한다: " + map.size());
        return map;
    }
}
