package com.darkness.wks.result;

import com.darkness.wks.saju.SajuPillars;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * A 모듈 연동 전 프론트엔드 개발에 사용하는 임시 분석 결과다.
 */
@Component
@ConditionalOnProperty(name = "app.result.fake-analysis-enabled", havingValue = "true")
public class FakeResultAnalysisAdapter implements ResultAnalysisPort {

    @Override
    public AnalysisResult analyze(LocalDate solarBirthDate, LocalTime birthTime) {
        SajuPillars pillars = new SajuPillars("임오", "계묘", "갑진", birthTime == null ? null : "신미");

        return new AnalysisResult(
                pillars,
                new Destiny(
                        "깔깔깔깔깔깔깔깔깔",
                        "당신은 특별한 운명을 타고났습니다. 앞으로 좋은 흐름을 맞이하게 됩니다."
                ),
                List.of(
                        new Fortune(FortuneCategory.MARRIAGE, "SS", "결혼운의 흐름이 매우 좋습니다."),
                        new Fortune(FortuneCategory.CHILDREN, "A+", "자녀운에 따뜻한 기운이 있습니다."),
                        new Fortune(FortuneCategory.LOVE, "C+", "연애에서는 천천히 마음을 확인하세요.")
                ),
                "파란색 팔찌",
                "야외 무대"
        );
    }
}
