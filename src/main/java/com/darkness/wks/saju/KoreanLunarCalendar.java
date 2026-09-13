package com.darkness.wks.saju;

import java.time.LocalDate;

/**
 * 한국 음력 → 양력 변환.
 * <p>
 * lunar-java 의 음력은 중국 기준이라 한국 음력과 달이 어긋나는 해가 있다(예: 1997년 설날 한국 2/8, 중국 2/7).
 * 그래서 음력 변환만 한국천문연구원(KASI) 음양력 표로 직접 처리한다.
 * 표는 yhj1024/manseryeok(MIT) 의 KASI 기반 테이블에서 1940~2030년 구간을 발췌했다.
 */
public final class KoreanLunarCalendar {

    public static final int MIN_YEAR = 1940;
    public static final int MAX_YEAR = 2030;

    /** 음력 1940-01-01 = 양력 1940-02-08 */
    private static final LocalDate BASE = LocalDate.of(1940, 2, 8);

    /**
     * 연도별 16비트 패킹: 0x8000~0x10 은 1~12월의 대(30일)/소(29일),
     * 하위 4비트는 윤달 위치(0=없음), 0x10000 은 윤달의 대소.
     */
    private static final int[] DATA = {
            0x0d4a0, 0x1d8a6, 0x0b690, 0x056d0, 0x125b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0d557, // 1940
            0x0b4a0, 0x0b550, 0x15555, 0x04db0, 0x025b0, 0x18573, 0x052b0, 0x0a9b8, 0x06950, 0x06aa0, // 1950
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05270, 0x07263, 0x0d950, 0x06b57, 0x056a0, // 1960
            0x09ad0, 0x04dd5, 0x04ae0, 0x0a4e0, 0x0d4d4, 0x0d250, 0x0d598, 0x0b540, 0x0d6a0, 0x195a6, // 1970
            0x095b0, 0x049b0, 0x0a9b4, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0b756, 0x02b60, 0x095b0, // 1980
            0x04b75, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06d98, 0x05ad0, 0x02b60, 0x096e5, 0x092e0, // 1990
            0x0c960, 0x0e954, 0x0d4a0, 0x0da50, 0x07552, 0x056c0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000
            0x0a950, 0x0b4a0, 0x1b4a3, 0x0b550, 0x055d9, 0x04ba0, 0x0a5b0, 0x05575, 0x052b0, 0x0a950, // 2010
            0x0b954, 0x06aa0, 0x0ad50, 0x06b52, 0x04b60, 0x0a6e6, 0x0a570, 0x05270, 0x06a65, 0x0d930, // 2020
            0x05aa0, // 2030
    };

    private KoreanLunarCalendar() {
    }

    /**
     * @param leapMonth 윤달 여부. 해당 연도에 그 달의 윤달이 없으면 예외
     * @throws IllegalArgumentException 지원 범위 밖이거나 존재하지 않는 음력 날짜
     */
    public static LocalDate toSolar(int year, int month, int day, boolean leapMonth) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException("지원하지 않는 음력 연도: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("잘못된 음력 월: " + month);
        }
        int leap = leapMonth(year);
        if (leapMonth && leap != month) {
            throw new IllegalArgumentException(year + "년에는 윤" + month + "월이 없습니다.");
        }
        int maxDay = leapMonth ? leapMonthDays(year) : monthDays(year, month);
        if (day < 1 || day > maxDay) {
            throw new IllegalArgumentException("잘못된 음력 일: " + day);
        }

        long offset = 0;
        for (int y = MIN_YEAR; y < year; y++) {
            offset += yearDays(y);
        }
        for (int m = 1; m < month; m++) {
            offset += monthDays(year, m);
            if (m == leap) {
                offset += leapMonthDays(year);
            }
        }
        if (leapMonth) {
            offset += monthDays(year, month); // 윤달은 같은 번호의 평달 다음에 온다
        }
        return BASE.plusDays(offset + day - 1);
    }

    static int leapMonth(int year) {
        return DATA[year - MIN_YEAR] & 0xf;
    }

    static int leapMonthDays(int year) {
        if (leapMonth(year) == 0) {
            return 0;
        }
        return (DATA[year - MIN_YEAR] & 0x10000) != 0 ? 30 : 29;
    }

    static int monthDays(int year, int month) {
        return (DATA[year - MIN_YEAR] & (0x10000 >> month)) != 0 ? 30 : 29;
    }

    static int yearDays(int year) {
        int days = 348; // 29일 × 12
        for (int bit = 0x8000; bit > 0x8; bit >>= 1) {
            if ((DATA[year - MIN_YEAR] & bit) != 0) {
                days++;
            }
        }
        return days + leapMonthDays(year);
    }
}
