package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirthDateTest {

    @Test
    void solarPassesThrough() {
        assertThat(BirthDate.parse(CalendarType.SOLAR, "2002-03-14", true).toSolar())
                .isEqualTo(LocalDate.of(2002, 3, 14));
    }

    @Test
    void lunarConvertsWithKoreanCalendar() {
        assertThat(BirthDate.parse(CalendarType.LUNAR, "1997-01-01", false).toSolar())
                .isEqualTo(LocalDate.of(1997, 2, 8));
        assertThat(BirthDate.parse(CalendarType.LUNAR, "2004-02-20", true).toSolar())
                .isEqualTo(LocalDate.of(2004, 4, 9)); // 윤2월
        // 음력 2월 30일은 LocalDate 로 표현 불가 → 문자열로 받는 이유
        assertThat(BirthDate.parse(CalendarType.LUNAR, "2001-02-30", false).toSolar())
                .isEqualTo(LocalDate.of(2001, 3, 24));
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> BirthDate.parse(CalendarType.SOLAR, "2002/03/14", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BirthDate.parse(CalendarType.SOLAR, "2001-02-30", false).toSolar())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BirthDate.parse(CalendarType.LUNAR, "2020-05-01", true).toSolar())
                .isInstanceOf(IllegalArgumentException.class); // 2020 윤달은 4월
    }
}
