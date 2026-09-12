package com.darkness.wks.result;

import com.darkness.wks.saju.SajuPillars;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 사주 계산·점수·해석을 제공하는 A 모듈과 result 패키지 사이의 연결 계약이다.
 */
public interface ResultAnalysisPort {

    AnalysisResult analyze(LocalDate birthDate, LocalTime birthTime, String birthRegion);

    record AnalysisResult(
            SajuPillars pillars,
            Destiny destiny,
            List<Fortune> fortunes,
            String luckyItem,
            String luckyPlace
    ) {

        public Fortune fortune(FortuneCategory category) {
            return fortunes.stream()
                    .filter(fortune -> fortune.category() == category)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing fortune: " + category));
        }
    }

    record Destiny(String title, String description) {
    }

    record Fortune(FortuneCategory category, String grade, String content) {
    }
}
