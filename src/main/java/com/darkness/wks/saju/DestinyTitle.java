package com.darkness.wks.saju;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 운명 제목 8종. 연애·결혼·자녀 각각 상(SS/S/A+)·하(A/B+/B) 조합 2×2×2 (기능명세서 유형 1~8 순서).
 * 제목은 {@code resources/destiny-titles.txt} ("연애결혼자녀=제목", 예: 상하상=…). 저장하지 않고 점수로 계산한다.
 */
public final class DestinyTitle {

    private static final List<String> KEYS = List.of("상상상", "상상하", "상하상", "상하하", "하상상", "하상하", "하하상", "하하하");
    private static final Map<String, String> TITLES = load();

    private DestinyTitle() {
    }

    public static String of(int marriageScore, int childrenScore, int loveScore) {
        return TITLES.get(level(loveScore) + level(marriageScore) + level(childrenScore));
    }

    /** 상 = A+ 이상 */
    static String level(int score) {
        return Grade.of(score).ordinal() <= Grade.A_PLUS.ordinal() ? "상" : "하";
    }

    /** 줄 수만 세면 키 오타("상하싱")가 통과해 of() 가 조용히 null 을 준다. 키 8개가 정확히 있는지 본다 */
    private static Map<String, String> load() {
        Map<String, String> map = LuckyPool.readKeyValues("destiny-titles.txt");
        if (!map.keySet().equals(Set.copyOf(KEYS))) {
            throw new IllegalStateException("destiny-titles.txt 의 키는 " + KEYS + " 여야 한다: " + map.keySet());
        }
        return map;
    }
}
