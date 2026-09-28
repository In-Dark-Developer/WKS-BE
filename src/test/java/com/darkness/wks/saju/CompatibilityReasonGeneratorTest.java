package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Gemini 는 호출하지 않는다 (TR-E-01). 프롬프트 조립만 검증 — 성별·닉네임·생년월일이 없고 오행 관계를 코드가 정하는지 */
class CompatibilityReasonGeneratorTest {

    @Test
    void promptHasPillarsElementsRelationScoreTierAndNoGender() {
        // A 일간 신(쇠), B 일간 갑(나무): 쇠가 나무를 누른다(상극)
        String prompt = CompatibilityReasonGenerator.buildPrompt(
                new SajuPillars("임오", "계묘", "신사", "을미"),
                new SajuPillars("정축", "을해", "갑자", null), 92, "귀인");
        assertThat(prompt).isEqualTo("""
                관계 유형: 귀인 / 궁합 점수: 92
                A 년주 임오, 월주 계묘, 일주 신사, 시주 을미
                A의 기운: 쇠 / A의 많은 기운: 나무, 불, 물
                B 년주 정축, 월주 을해, 일주 갑자, 시주 모름
                B의 기운: 나무 / B의 많은 기운: 나무, 물
                두 기운의 관계: 쇠가 나무를 누른다(상극)""");
        assertThat(prompt).doesNotContain("남성", "여성");
    }

    @Test
    void relationCoversGenerateAndSame() {
        SajuPillars wood = new SajuPillars("갑인", "갑인", "갑인", "갑인");
        SajuPillars fire = new SajuPillars("병오", "병오", "병오", "병오");
        assertThat(CompatibilityReasonGenerator.buildPrompt(wood, fire, 80, "찰떡")).contains("나무가 불을 살린다(상생)");
        assertThat(CompatibilityReasonGenerator.buildPrompt(fire, wood, 80, "찰떡")).contains("나무가 불을 살린다(상생)");
        assertThat(CompatibilityReasonGenerator.buildPrompt(wood, wood, 70, "벗")).contains("두 기운의 관계: 같은 기운");
    }

    @Test
    void bankHitSkipsLlmAndMissFallsBackToLlm() {
        GeminiJson gemini = mock(GeminiJson.class);
        SajuPillars wood = new SajuPillars("갑인", "갑인", "갑인", "갑인");
        SajuPillars fire = new SajuPillars("병오", "병오", "병오", "병오");
        CompatibilityReason banked = new CompatibilityReason("은행 왜", "은행 만나면", "은행 싸우면");
        CompatibilityReasonGenerator generator = new CompatibilityReasonGenerator(gemini,
                new CompatibilityReasonBank(Map.of("찰떡|나무:나무|불:불", List.of(banked))));

        assertThat(generator.generate(fire, wood, 80, "찰떡", 0, Set.of())).isEqualTo(banked);
        verifyNoInteractions(gemini);

        when(gemini.generate(anyString(), any(), anyList()))
                .thenReturn(Map.of("why", "왜", "together", "만나면", "conflict", "싸우면"));
        assertThat(generator.generate(fire, wood, 70, "벗", 0, Set.of()))
                .isEqualTo(new CompatibilityReason("왜", "만나면", "싸우면"));
    }
}
