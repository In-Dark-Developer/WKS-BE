package com.darkness.wks.result;

import com.darkness.wks.common.Gender;
import com.darkness.wks.saju.SajuPillars;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 사주 계산·점수·해석을 제공하는 A 모듈과 result 패키지 사이의 연결 계약이다.
 */
public interface ResultAnalysisPort {

    /**
     * @param solarBirthDate 양력 생년월일. 음력 입력은 호출 전에 {@code BirthDate.parse(...).toSolar()} 로 변환한다
     * @param birthTime      null 이면 시간 모름
     * @param gender         배우자성·자녀성 판정과 해석 문장의 역할 표현에 쓴다
     */
    AnalysisResult analyze(LocalDate solarBirthDate, LocalTime birthTime, Gender gender);

    /** 프롬프트·모델·점수 로직이 바뀌면 달라지는 값. 같은 입력 해석 재사용은 이 값이 같을 때만 (#62) */
    int analysisVersion();

    record AnalysisResult(
            SajuPillars pillars,
            String destinyDescription,
            List<Fortune> fortunes,
            String elementMatchReason // "나와 잘 맞는 오행" 풀이. 오행 자체는 팔자에서 계산하므로 저장하지 않는다 (#82)
    ) {

        public Fortune fortune(FortuneCategory category) {
            return fortunes.stream()
                    .filter(fortune -> fortune.category() == category)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing fortune: " + category));
        }
    }

    /** score 는 0~100. 등급은 응답 시 {@code Grade.of(score)} 로 만든다 */
    record Fortune(FortuneCategory category, int score, String content) {
    }
}
