package com.darkness.wks.saju;

/**
 * 십이간지(띠). 연주 지지로 정한다 — 양력 연도가 아니라 **입춘 기준**이라 1~2월생은 전년 띠일 수 있다.
 * 캐릭터 이름·이모지 매핑은 프론트 몫. API 에는 enum 이름만 나간다.
 */
public enum Zodiac {
    RAT, OX, TIGER, RABBIT, DRAGON, SNAKE, HORSE, GOAT, MONKEY, ROOSTER, DOG, PIG;

    private static final String BRANCHES = "자축인묘진사오미신유술해";

    /** @param yearPillar 연주 2글자 (예: "계미") */
    public static Zodiac fromYearPillar(String yearPillar) {
        return values()[BRANCHES.indexOf(yearPillar.charAt(1))];
    }
}
