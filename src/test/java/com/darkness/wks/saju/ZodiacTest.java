package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ZodiacTest {

    private final SajuCalculator calculator = new SajuCalculator();

    @Test
    void zodiacFollowsYearBranch() {
        assertThat(new SajuPillars("계미", "을축", "계축", "신유").zodiac()).isEqualTo(Zodiac.GOAT);
        assertThat(new SajuPillars("경진", "기축", "무술", "임자").zodiac()).isEqualTo(Zodiac.DRAGON);
    }

    @Test
    void zodiacChangesAtIpchunNotNewYear() {
        // 2004-02-04 입춘 20:56. 같은 양력 2004년이라도 입춘 전은 양띠, 후는 원숭이띠
        assertThat(calculator.calculate(LocalDate.of(2004, 2, 4), LocalTime.of(19, 0), null).zodiac())
                .isEqualTo(Zodiac.GOAT);
        assertThat(calculator.calculate(LocalDate.of(2004, 2, 4), LocalTime.of(21, 30), null).zodiac())
                .isEqualTo(Zodiac.MONKEY);
    }
}
