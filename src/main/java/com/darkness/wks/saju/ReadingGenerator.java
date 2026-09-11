package com.darkness.wks.saju;

import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 팔자와 점수를 바탕으로 카테고리별 해석 텍스트를 생성한다.
 * Gemini 클라이언트 빈/타임아웃 설정까지만 되어 있고, 프롬프트 로직은 비어 있다.
 */
@Component
@RequiredArgsConstructor
public class ReadingGenerator {

    private final Client geminiClient;

    public String generate(SajuPillars pillars, ReadingCategory category, int score) {
        // TODO: 프롬프트 설계 및 Gemini API 호출 로직 구현 (5개 카테고리 한 번의 호출로 생성 — FR-GM-02)
        throw new UnsupportedOperationException("ReadingGenerator is not implemented yet");
    }
}
