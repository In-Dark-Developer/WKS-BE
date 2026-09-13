package com.darkness.wks.result;

import com.darkness.wks.saju.Grade;
import com.darkness.wks.saju.Reading;
import com.darkness.wks.saju.ReadingCategory;
import com.darkness.wks.saju.ReadingGenerator;
import com.darkness.wks.saju.SajuPillars;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Gemini 는 목킹한다 (TR-E-01). 팔자·등급이 계약대로 어댑터를 통과하는지만 본다. */
class SajuResultAnalysisAdapterTest {

    @Test
    void mapsPillarsGradesAndContentsIntoContract() {
        ReadingGenerator generator = mock(ReadingGenerator.class);
        when(generator.generate(any(), any())).thenReturn(new Reading(
                "설명",
                Map.of(ReadingCategory.MARRIAGE, "결혼", ReadingCategory.CHILDREN, "자녀", ReadingCategory.LOVE, "연애")));
        SajuResultAnalysisAdapter adapter = new SajuResultAnalysisAdapter(generator);

        ResultAnalysisPort.AnalysisResult r = adapter.analyze(LocalDate.of(2002, 3, 14), LocalTime.of(14, 30));

        assertThat(r.pillars()).isEqualTo(new SajuPillars("임오", "계묘", "신사", "을미"));
        assertThat(r.fortunes()).extracting(ResultAnalysisPort.Fortune::category)
                .containsExactly(FortuneCategory.MARRIAGE, FortuneCategory.CHILDREN, FortuneCategory.LOVE);
        assertThat(r.fortune(FortuneCategory.LOVE).content()).isEqualTo("연애");
        assertThat(r.destinyDescription()).isEqualTo("설명");

        // 점수는 코드가 정하고, LLM 에는 그 점수의 등급이 그대로 전달된다 (FR-RD-02)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<ReadingCategory, Grade>> grades = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(generator).generate(any(), grades.capture());
        assertThat(r.fortunes()).extracting(f -> Grade.of(f.score()))
                .containsExactly(
                        grades.getValue().get(ReadingCategory.MARRIAGE),
                        grades.getValue().get(ReadingCategory.CHILDREN),
                        grades.getValue().get(ReadingCategory.LOVE));
    }

    @Test
    void unknownTimeHasNoHourPillar() {
        ReadingGenerator generator = mock(ReadingGenerator.class);
        when(generator.generate(any(), any())).thenReturn(new Reading("d",
                Map.of(ReadingCategory.MARRIAGE, "a", ReadingCategory.CHILDREN, "b", ReadingCategory.LOVE, "c")));

        ResultAnalysisPort.AnalysisResult r = new SajuResultAnalysisAdapter(generator)
                .analyze(LocalDate.of(2002, 3, 14), null);

        assertThat(r.pillars().hourPillar()).isNull();
    }
}
