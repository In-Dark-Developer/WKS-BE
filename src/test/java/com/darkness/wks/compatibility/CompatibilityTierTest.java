package com.darkness.wks.compatibility;

import com.darkness.wks.compatibility.entity.CompatibilityTier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CompatibilityTierTest {

    @ParameterizedTest
    @CsvSource({
            "0, SEUCHIM",
            "60, SEUCHIM",
            "61, BEOT",
            "74, BEOT",
            "75, CHALTTEOK",
            "89, CHALTTEOK",
            "90, GUIIN",
            "100, GUIIN"
    })
    void mapsBoundaryScores(int score, CompatibilityTier expected) {
        assertThat(CompatibilityTier.fromScore(score)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void rejectsOutOfRangeScore(int score) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CompatibilityTier.fromScore(score));
    }
}
