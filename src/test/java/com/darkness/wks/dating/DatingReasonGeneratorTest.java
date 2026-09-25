package com.darkness.wks.dating;

import com.darkness.wks.saju.GeminiJson;
import com.darkness.wks.saju.SajuPillars;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatingReasonGeneratorTest {

    @Test
    void usesOneGeminiCallWithOnlyPillarsAndComputedFacts() {
        GeminiJson gemini = mock(GeminiJson.class);
        DatingReasonGenerator generator = new DatingReasonGenerator(gemini);
        SajuPillars viewer = new SajuPillars("갑자", "을축", "병인", null);
        SajuPillars candidate = new SajuPillars("계해", "임술", "기유", "경신");
        when(gemini.generate(anyString(), anyString(), eq(List.of("reason"))))
                .thenReturn(Map.of("reason", "서로의 기운이 어울려요."));

        assertThat(generator.generate(viewer, candidate, 82, "찰떡"))
                .isEqualTo("서로의 기운이 어울려요.");
        String prompt = DatingReasonGenerator.buildPrompt(viewer, candidate, 82, "찰떡");
        assertThat(prompt).contains("갑자", "기유", "82", "찰떡", "상생");
        assertThat(prompt).doesNotContain("2002", "닉네임", "생년월일", "null");
        verify(gemini).generate(anyString(), eq(prompt), eq(List.of("reason")));
    }
}
