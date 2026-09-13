package com.darkness.wks.saju;

import java.util.Map;

/** LLM 이 각색한 해석 텍스트. 등급은 {@link ReadingScorer}, 운명 제목은 {@link DestinyTitle}, 행운은 {@link DailyLucky} 가 정한다. */
public record Reading(
        String destinyDescription,
        Map<ReadingCategory, String> contents
) {
}
