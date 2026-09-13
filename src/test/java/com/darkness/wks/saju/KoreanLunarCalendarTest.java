package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KoreanLunarCalendarTest {

    @Test
    void koreanLunarDiffersFromChinese() {
        // 1997 설날: 한국 2/8, 중국 2/7
        assertThat(KoreanLunarCalendar.toSolar(1997, 1, 1, false)).isEqualTo(LocalDate.of(1997, 2, 8));
    }

    @Test
    void convertsRegularAndLeapMonths() {
        assertThat(KoreanLunarCalendar.toSolar(1940, 1, 1, false)).isEqualTo(LocalDate.of(1940, 2, 8));
        assertThat(KoreanLunarCalendar.toSolar(2001, 1, 12, false)).isEqualTo(LocalDate.of(2001, 2, 4));
        assertThat(KoreanLunarCalendar.toSolar(2010, 11, 26, false)).isEqualTo(LocalDate.of(2010, 12, 31));
        assertThat(KoreanLunarCalendar.toSolar(2020, 4, 1, true)).isEqualTo(LocalDate.of(2020, 5, 23)); // 윤4월
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> KoreanLunarCalendar.toSolar(2020, 5, 1, true))
                .isInstanceOf(IllegalArgumentException.class); // 2020년 윤달은 4월
        assertThatThrownBy(() -> KoreanLunarCalendar.toSolar(2020, 4, 30, true))
                .isInstanceOf(IllegalArgumentException.class); // 2020 윤4월은 29일
        assertThatThrownBy(() -> KoreanLunarCalendar.toSolar(1939, 1, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
