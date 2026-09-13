package com.darkness.wks.compatibility;

import com.darkness.wks.saju.SajuPillars;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CompatibilityCalculatorTest {

    private final CompatibilityCalculator calculator = new CompatibilityCalculator();

    @Test
    void scoreIsSymmetric() {
        SajuPillars first = new SajuPillars("임오", "계묘", "갑진", "신미");
        SajuPillars second = new SajuPillars("정축", "을해", "기유", "병자");

        assertThat(calculator.calculate(first, second))
                .isEqualTo(calculator.calculate(second, first));
    }

    @Test
    void scoreIsBetweenZeroAndOneHundred() {
        SajuPillars first = new SajuPillars("갑자", "병인", "무진", "경오");
        SajuPillars second = new SajuPillars("기축", "신해", "계유", "을미");

        assertThat(calculator.calculate(first, second)).isBetween(0, 100);
    }

    @Test
    void harmoniousEightCharactersScoreHigherThanClashingCharacters() {
        SajuPillars origin = new SajuPillars("갑자", "갑자", "갑자", "갑자");
        SajuPillars harmonious = new SajuPillars("기축", "기축", "기축", "기축");
        SajuPillars clashing = new SajuPillars("경오", "경오", "경오", "경오");

        int harmoniousScore = calculator.calculate(origin, harmonious);
        int clashingScore = calculator.calculate(origin, clashing);

        assertThat(harmoniousScore).isGreaterThanOrEqualTo(76);
        assertThat(clashingScore).isLessThanOrEqualTo(25);
        assertThat(harmoniousScore).isGreaterThan(clashingScore);
    }

    @Test
    void calculatesWithoutHourPillar() {
        SajuPillars first = new SajuPillars("임오", "계묘", "갑진", null);
        SajuPillars second = new SajuPillars("정축", "을해", "기유", null);

        assertThat(calculator.calculate(first, second)).isBetween(0, 100);
    }

    @Test
    void rejectsInvalidPillar() {
        SajuPillars invalid = new SajuPillars("임오", "계묘", "잘못", null);
        SajuPillars valid = new SajuPillars("정축", "을해", "기유", null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(invalid, valid));
    }
}
