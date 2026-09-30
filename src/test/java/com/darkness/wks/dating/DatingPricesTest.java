package com.darkness.wks.dating;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DatingPricesTest {

    @AfterEach
    void restoreClock() {
        DatingPrices.clock = Clock.systemUTC();
    }

    @Test
    void 할인_시작_직전까지는_정가() {
        pin(DatingPrices.FINAL_DAY_SALE_START.minusSeconds(1));

        assertThat(DatingPrices.reroll()).isEqualTo(20);
        assertThat(DatingUnlockField.PHOTO.cost()).isEqualTo(10);
        assertThat(DatingUnlockField.NAME.cost()).isEqualTo(7);
        assertThat(DatingUnlockField.DEPARTMENT.cost()).isEqualTo(5);
        assertThat(DatingUnlockField.REASON.cost()).isEqualTo(3);
    }

    @Test
    void 시월_일일_오전_열시_KST_부터_할인가() {
        assertThat(DatingPrices.FINAL_DAY_SALE_START).isEqualTo(Instant.parse("2026-10-01T01:00:00Z"));
        pin(DatingPrices.FINAL_DAY_SALE_START);

        assertThat(DatingPrices.reroll()).isEqualTo(10);
        assertThat(DatingUnlockField.PHOTO.cost()).isEqualTo(5);
        assertThat(DatingUnlockField.NAME.cost()).isEqualTo(3);
        assertThat(DatingUnlockField.DEPARTMENT.cost()).isEqualTo(2);
        assertThat(DatingUnlockField.REASON.cost()).isEqualTo(1);
    }

    private static void pin(Instant instant) {
        DatingPrices.clock = Clock.fixed(instant, ZoneOffset.UTC);
    }
}
