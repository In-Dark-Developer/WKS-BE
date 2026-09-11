package com.darkness.wks.saju;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 생년월일시·지역으로 사주 4주(년/월/일/시주)를 계산한다.
 * 만세력 라이브러리가 아직 미정이므로 스텁만 존재한다.
 * DB(Repository/Entity)에 의존하지 않는 순수 클래스로, 단위 테스트가 가능해야 한다.
 */
public class SajuCalculator {

    public SajuPillars calculate(LocalDate birthDate, LocalTime birthTime, String birthRegion) {
        // TODO: 만세력 라이브러리 확정 후 팔자 계산 로직 구현
        throw new UnsupportedOperationException("SajuCalculator is not implemented yet");
    }
}
