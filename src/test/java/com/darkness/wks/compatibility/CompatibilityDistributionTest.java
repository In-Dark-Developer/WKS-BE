package com.darkness.wks.compatibility;

import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.saju.SajuCalculator;
import com.darkness.wks.saju.SajuPillars;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityDistributionTest {

    @Test
    void tiersFollowTargetDistributionForRealPillars() {
        SajuCalculator sajuCalculator = new SajuCalculator();
        CompatibilityCalculator compatibilityCalculator = new CompatibilityCalculator();
        Random random = new Random(20260913L);
        LocalDate start = LocalDate.of(1950, 1, 1);
        long days = LocalDate.of(2010, 12, 31).toEpochDay() - start.toEpochDay() + 1;
        List<SajuPillars> pool = new ArrayList<>();

        for (int i = 0; i < 3_000; i++) {
            LocalDate date = start.plusDays(random.nextLong(days));
            LocalTime time = LocalTime.of(random.nextInt(24), random.nextInt(60));
            pool.add(sajuCalculator.calculate(date, time, null));
        }

        int[] tierCounts = new int[CompatibilityTier.values().length];
        for (int i = 0; i < 100_000; i++) {
            int score = compatibilityCalculator.calculate(
                    pool.get(random.nextInt(pool.size())),
                    pool.get(random.nextInt(pool.size()))
            );
            tierCounts[CompatibilityTier.fromScore(score).ordinal()]++;
        }

        assertThat(tierCounts[CompatibilityTier.GUIIN.ordinal()]).isBetween(18_000, 22_000);
        assertThat(tierCounts[CompatibilityTier.CHALTTEOK.ordinal()]).isBetween(28_000, 32_000);
        assertThat(tierCounts[CompatibilityTier.BEOT.ordinal()]).isBetween(28_000, 32_000);
        assertThat(tierCounts[CompatibilityTier.SEUCHIM.ordinal()]).isBetween(18_000, 22_000);
    }
}
