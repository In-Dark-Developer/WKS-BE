package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기대값은 KASI 기준 manseryeok 2.0.0 (서울 경도 126.978, 균시차·서머타임 OFF, 야자시=다음날) 출력.
 */
class SajuCalculatorTest {

    private final SajuCalculator calculator = new SajuCalculator();

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "1936-08-25, 07:30, 병자 병신 기묘 정묘",
            "1948-05-01, 12:00, 무자 병진 병술 갑오",
            "1960-03-15, 10:00, 경자 기묘 임인 을사",
            "1975-07-07, 14:00, 을묘 임오 갑인 신미",
            "1984-06-15, 09:00, 갑자 경오 경진 경진",
            "1988-09-20, 16:00, 무진 신유 무인 경신",
            "1992-10-24, 05:30, 임신 경술 계유 갑인",
            "1995-11-11, 11:11, 을해 정해 병오 계사",
            "2000-06-10, 08:30, 경진 임오 기해 무진",
            "2010-12-05, 18:00, 경인 정해 기축 계유",
            "2020-04-20, 13:00, 경자 경진 계사 무오",
    })
    void goldenCases(LocalDate date, LocalTime time, String expected) {
        assertThat(join(calculator.calculate(date, time, "서울"))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "입춘 경계 {0} {1} → {2}")
    @CsvSource({
            "2024-02-04, 17:00, 계묘 을축 무술 경신", // 입춘 17:27 KST 직전: 전년 연주·월주
            "2024-02-04, 17:40, 갑진 병인 무술 신유", // 직후
            "2001-02-04, 03:00, 경진 기축 무술 계축", // 입춘 03:29 KST 직전
            "2001-02-04, 04:00, 신사 경인 무술 갑인",
    })
    void solarTermBoundary(LocalDate date, LocalTime time, String expected) {
        assertThat(join(calculator.calculate(date, time, null))).isEqualTo(expected);
    }

    /** 포스텔러 만세력 2.2 (야자시/조자시 미체크, 지역시 적용) 실제 대조 결과. 2026-09-13 */
    @ParameterizedTest(name = "포스텔러 {0} {1} {2} → {3}")
    @CsvSource({
            "2000-01-01, 00:15, 인천광역시, 기묘 병자 무오 임자",
            "2004-02-04, 19:00, 광주광역시, 계미 을축 계축 신유",
            "2004-02-04, 21:10, 광주광역시, 갑신 병인 계축 임술", // 진태양시 20:37 이지만 절기는 KST 로 판정
            "2004-02-04, 21:30, 광주광역시, 갑신 병인 계축 임술",
            "2003-07-04, 23:40, 대구광역시, 계미 무오 기묘 갑자",
            "1999-08-08, 05:30, 대전광역시, 기묘 신미 임진 임인",
            "2002-06-21, 21:10, 수원시, 임오 병오 경신 병술",
            "1997-02-08, 03:30, 서울특별시, 정축 임인 신사 기축",
    })
    void matchesPosteller(LocalDate date, LocalTime time, String region, String expected) {
        assertThat(join(calculator.calculate(date, time, region))).isEqualTo(expected);
    }

    @Test
    void dayBoundaryIsStartOfJasiInTrueSolarTime() {
        // 23:30 KST = 서울 진태양시 22:58 → 해시, 당일. 23:40 = 23:08 → 야자시, 다음날 일주·시주
        assertThat(join(calculator.calculate(LocalDate.of(2002, 3, 14), LocalTime.of(23, 30), "서울")))
                .isEqualTo("임오 계묘 신사 기해");
        assertThat(join(calculator.calculate(LocalDate.of(2002, 3, 14), LocalTime.of(23, 40), "서울")))
                .isEqualTo("임오 계묘 임오 경자");
    }

    @Test
    void unknownTimeOmitsHourPillar() {
        SajuPillars p = calculator.calculate(LocalDate.of(2002, 3, 14), null, null);
        assertThat(p.hourPillar()).isNull();
        assertThat(p.yearPillar() + " " + p.monthPillar() + " " + p.dayPillar()).isEqualTo("임오 계묘 신사");
    }

    private static String join(SajuPillars p) {
        return p.yearPillar() + " " + p.monthPillar() + " " + p.dayPillar() + " " + p.hourPillar();
    }
}
