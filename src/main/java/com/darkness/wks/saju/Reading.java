package com.darkness.wks.saju;

import java.util.Map;

/**
 * LLM 이 각색한 해석 텍스트. 등급은 {@link ReadingScorer}, 운명 제목은 {@link DestinyTitle}, 행운은 {@link DailyLucky},
 * 잘 맞는 오행 자체는 {@link LuckyPlace#luckyElement} 가 정한다. 여기엔 문장만 있다.
 */
public record Reading(
        String destinyDescription,
        Map<ReadingCategory, String> contents,
        String elementMatch // "나와 잘 맞는 오행" 풀이 (기능명세 3.5, #82)
) {
}
