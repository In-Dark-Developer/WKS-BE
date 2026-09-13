package com.darkness.wks.saju;

import java.util.Map;

/** LLM 이 각색한 해석 텍스트. 등급은 {@link ReadingScorer}, 행운 아이템·장소는 {@link DailyLucky} 가 정한다. */
public record Reading(
        String destinyTitle,
        String destinyDescription,
        Map<ReadingCategory, String> contents
) {
}
