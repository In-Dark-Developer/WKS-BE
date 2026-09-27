package com.darkness.wks.dating;

import java.time.LocalDate;

/**
 * 소개팅 카드에 보여줄 나이 표기("00년생"). 연도 끝 두 자리만 쓰므로 생년월일을 역산할 수 없고,
 * 해가 바뀌어도 값이 그대로라 기준 시간대를 따질 필요가 없다(2026-09-27 결정).
 */
final class BirthYearLabel {

    private BirthYearLabel() {
    }

    static String of(LocalDate birthDate) {
        return String.format("%02d년생", birthDate.getYear() % 100);
    }
}
