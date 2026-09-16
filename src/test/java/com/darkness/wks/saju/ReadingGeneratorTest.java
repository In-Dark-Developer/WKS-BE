package com.darkness.wks.saju;

import com.darkness.wks.common.Gender;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gemini 는 호출하지 않는다 (TR-E-01). 프롬프트 조립과 응답 파싱만 검증. */
class ReadingGeneratorTest {

    private static final Map<ReadingCategory, Grade> GRADES = Map.of(
            ReadingCategory.MARRIAGE, Grade.SS, ReadingCategory.CHILDREN, Grade.A_PLUS, ReadingCategory.LOVE, Grade.B);

    @Test
    void promptContainsGenderPillarsElementFactsAndGrades() {
        // 임오 계묘 신사 을미: 목 2(묘·을) 화 2(오·사) 토 1(미) 금 1(신) 수 2(임·계) → 많은 기운 목·화·수, 없는 기운 없음
        // 일간 신(쇠). 남자: 배우자성 재성 = 나무, 자녀성 관성 = 불. 일지 사 = 불
        String prompt = ReadingGenerator.buildPrompt(new SajuPillars("임오", "계묘", "신사", "을미"), GRADES, Gender.MALE);
        assertThat(prompt).isEqualTo("""
                성별 남성
                년주 임오, 월주 계묘, 일주 신사, 시주 을미
                나의 기운: 쇠
                많은 기운: 나무, 불, 물 / 없는 기운: 없음
                배우자 기운: 나무 / 배우자 자리의 기운: 불
                자녀 기운: 불
                결혼운 등급: SS
                자녀운 등급: A+
                연애운 등급: B""");
        // 여자: 배우자성 관성 = 불, 자녀성 식상 = 물
        assertThat(ReadingGenerator.buildPrompt(new SajuPillars("임오", "계묘", "신사", "을미"), GRADES, Gender.FEMALE))
                .contains("배우자 기운: 불 /").contains("자녀 기운: 물");
    }

    @Test
    void strongAndWeakElementsMatchCharacterCounts() {
        // 갑인 갑인 갑인 갑인: 목 8, 나머지 0
        assertThat(ReadingGenerator.buildPrompt(new SajuPillars("갑인", "갑인", "갑인", "갑인"), GRADES, Gender.MALE))
                .contains("많은 기운: 나무 / 없는 기운: 불, 흙, 쇠, 물");
    }

    @Test
    void promptMarksUnknownHour() {
        assertThat(ReadingGenerator.buildPrompt(new SajuPillars("임오", "계묘", "신사", null), GRADES, Gender.FEMALE))
                .contains("시주 모름");
    }

    @Test
    void parsesCompleteJson() {
        Reading r = new ReadingGenerator(null, "m").parse("""
                {"destinyDescription":"설명","marriage":"결혼","children":"자녀","love":"연애"}""");
        assertThat(r.destinyDescription()).isEqualTo("설명");
        assertThat(r.contents()).containsEntry(ReadingCategory.LOVE, "연애");
    }

    @Test
    void rejectsMissingFieldOrBrokenJson() {
        ReadingGenerator g = new ReadingGenerator(null, "m");
        assertThatThrownBy(() -> g.parse("{\"destinyDescription\":\"x\"}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> g.parse("not json")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> g.parse(null)).isInstanceOf(IllegalStateException.class);
    }
}
