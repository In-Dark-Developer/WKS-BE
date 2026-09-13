package com.darkness.wks.saju;

/** 한글 2글자 간지. hourPillar 는 시간 모름이면 null. */
public record SajuPillars(String yearPillar, String monthPillar, String dayPillar, String hourPillar) {

    public Zodiac zodiac() {
        return Zodiac.fromYearPillar(yearPillar);
    }
}
