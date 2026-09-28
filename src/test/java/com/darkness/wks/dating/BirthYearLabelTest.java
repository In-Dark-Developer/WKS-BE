package com.darkness.wks.dating;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BirthYearLabelTest {

    @Test
    void usesLastTwoDigitsOfBirthYearWithLeadingZero() {
        assertThat(BirthYearLabel.of(LocalDate.of(2000, 12, 31))).isEqualTo("00년생");
        assertThat(BirthYearLabel.of(LocalDate.of(2005, 1, 1))).isEqualTo("05년생");
        assertThat(BirthYearLabel.of(LocalDate.of(1998, 6, 15))).isEqualTo("98년생");
    }
}
