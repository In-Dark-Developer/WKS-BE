package com.darkness.wks.saju;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 요청으로 들어온 생년월일(양력 또는 음력)을 양력 {@link LocalDate} 로 바꾼다.
 * <p>
 * 음력은 2월 30일처럼 {@link LocalDate} 가 표현 못 하는 날짜가 있어 문자열로 받는다.
 * 호출 측(result/)은 {@code BirthDate.parse(...).toSolar()} 한 줄로 변환하고, 그 양력 날짜를 저장·계산에 쓴다.
 */
public record BirthDate(CalendarType calendarType, int year, int month, int day, boolean leapMonth) {

    private static final Pattern YYYY_MM_DD = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");

    /**
     * @param yyyyMMdd  {@code yyyy-MM-dd}
     * @param leapMonth 음력 윤달 여부. 양력이면 무시
     * @throws IllegalArgumentException 형식이 다르거나 존재하지 않는 날짜
     */
    public static BirthDate parse(CalendarType calendarType, String yyyyMMdd, boolean leapMonth) {
        Matcher m = YYYY_MM_DD.matcher(yyyyMMdd == null ? "" : yyyyMMdd);
        if (!m.matches()) {
            throw new IllegalArgumentException("생년월일 형식은 yyyy-MM-dd 여야 합니다: " + yyyyMMdd);
        }
        return new BirthDate(calendarType, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), calendarType == CalendarType.LUNAR && leapMonth);
    }

    /** @throws IllegalArgumentException 존재하지 않는 날짜, 없는 윤달, 지원 범위 밖 음력 연도 */
    public LocalDate toSolar() {
        if (calendarType == CalendarType.LUNAR) {
            return KoreanLunarCalendar.toSolar(year, month, day, leapMonth);
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("존재하지 않는 양력 날짜: " + year + "-" + month + "-" + day, e);
        }
    }
}
