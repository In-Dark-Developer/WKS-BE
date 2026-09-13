package com.darkness.wks.result;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class FakeResultAnalysisAdapterTest {

    private final FakeResultAnalysisAdapter adapter = new FakeResultAnalysisAdapter();

    @Test
    void returnsFortunesInApiOrder() {
        ResultAnalysisPort.AnalysisResult result = adapter.analyze(
                LocalDate.of(2002, 3, 14),
                LocalTime.of(14, 30)
        );

        assertThat(result.fortunes())
                .extracting(ResultAnalysisPort.Fortune::category)
                .containsExactly(
                        FortuneCategory.MARRIAGE,
                        FortuneCategory.CHILDREN,
                        FortuneCategory.LOVE
                );
    }

    @Test
    void omitsHourPillarWhenBirthTimeIsUnknown() {
        ResultAnalysisPort.AnalysisResult result = adapter.analyze(
                LocalDate.of(2002, 3, 14),
                null
        );

        assertThat(result.pillars().hourPillar()).isNull();
    }
}
