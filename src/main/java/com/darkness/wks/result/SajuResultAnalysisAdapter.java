package com.darkness.wks.result;

import com.darkness.wks.saju.Grade;
import com.darkness.wks.saju.Reading;
import com.darkness.wks.saju.ReadingCategory;
import com.darkness.wks.saju.ReadingGenerator;
import com.darkness.wks.saju.ReadingScorer;
import com.darkness.wks.saju.SajuCalculator;
import com.darkness.wks.saju.SajuPillars;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 실제 분석기: SajuCalculator(팔자) → ReadingScorer(점수) → Grade.of(등급) → ReadingGenerator(해석, Gemini 1회).
 */
@Component
@RequiredArgsConstructor
public class SajuResultAnalysisAdapter implements ResultAnalysisPort {

    private final ReadingGenerator readingGenerator;
    private final SajuCalculator sajuCalculator = new SajuCalculator();
    private final ReadingScorer readingScorer = new ReadingScorer();

    @Override
    public AnalysisResult analyze(LocalDate solarBirthDate, LocalTime birthTime) {
        SajuPillars pillars = sajuCalculator.calculate(solarBirthDate, birthTime, null); // 지역 미수집 → 서울 기준

        Map<ReadingCategory, Integer> scores = readingScorer.score(pillars);
        Map<ReadingCategory, Grade> grades = new EnumMap<>(ReadingCategory.class);
        scores.forEach((category, score) -> grades.put(category, Grade.of(score)));

        Reading reading = readingGenerator.generate(pillars, grades);

        return new AnalysisResult(
                pillars,
                reading.destinyDescription(),
                List.of(
                        fortune(FortuneCategory.MARRIAGE, ReadingCategory.MARRIAGE, scores, reading),
                        fortune(FortuneCategory.CHILDREN, ReadingCategory.CHILDREN, scores, reading),
                        fortune(FortuneCategory.LOVE, ReadingCategory.LOVE, scores, reading)
                )
        );
    }

    private static Fortune fortune(FortuneCategory to, ReadingCategory from, Map<ReadingCategory, Integer> scores, Reading reading) {
        return new Fortune(to, scores.get(from), reading.contents().get(from));
    }
}
