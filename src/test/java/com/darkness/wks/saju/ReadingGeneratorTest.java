package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gemini 는 호출하지 않는다 (TR-E-01). 프롬프트 조립과 응답 파싱만 검증. */
class ReadingGeneratorTest {

    private static final Map<ReadingCategory, Grade> GRADES = Map.of(
            ReadingCategory.MARRIAGE, Grade.SS, ReadingCategory.CHILDREN, Grade.A_PLUS, ReadingCategory.LOVE, Grade.B);

    @Test
    void promptContainsOnlyPillarsAndGrades() {
        String prompt = ReadingGenerator.buildPrompt(new SajuPillars("임오", "계묘", "신사", "을미"), GRADES);
        assertThat(prompt).isEqualTo("년주 임오, 월주 계묘, 일주 신사, 시주 을미\n결혼운 등급: SS\n자녀운 등급: A+\n연애운 등급: B");
        assertThat(prompt).doesNotContainPattern("\\d{4}"); // 생년 등 숫자 정보 없음 (TR-03)
    }

    @Test
    void promptMarksUnknownHour() {
        assertThat(ReadingGenerator.buildPrompt(new SajuPillars("임오", "계묘", "신사", null), GRADES))
                .contains("시주 모름");
    }

    @Test
    void parsesCompleteJson() {
        Reading r = new ReadingGenerator(null, "m").parse("""
                {"destinyTitle":"달빛 실","destinyDescription":"설명","marriage":"결혼","children":"자녀","love":"연애"}""");
        assertThat(r.destinyTitle()).isEqualTo("달빛 실");
        assertThat(r.contents()).containsEntry(ReadingCategory.LOVE, "연애");
    }

    @Test
    void rejectsMissingFieldOrBrokenJson() {
        ReadingGenerator g = new ReadingGenerator(null, "m");
        assertThatThrownBy(() -> g.parse("{\"destinyTitle\":\"x\"}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> g.parse("not json")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> g.parse(null)).isInstanceOf(IllegalStateException.class);
    }
}
