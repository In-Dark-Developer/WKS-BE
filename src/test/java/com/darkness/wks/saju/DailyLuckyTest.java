package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyLuckyTest {

    private static final SajuPillars ME = new SajuPillars("임오", "계묘", "신사", "을미");

    @Test
    void todayPillarAndGanjiIndex() {
        assertThat(DailyLucky.todayPillar(LocalDate.of(2000, 1, 1))).isEqualTo("무오"); // 2000-01-01 일진
        assertThat(DailyLucky.ganjiIndex("갑자")).isEqualTo(0);
        assertThat(DailyLucky.ganjiIndex("을축")).isEqualTo(1);
        assertThat(DailyLucky.ganjiIndex("계해")).isEqualTo(59);
    }

    @Test
    void deterministicPerDayAndChangesAcrossDays() {
        LocalDate d = LocalDate.of(2026, 9, 29);
        assertThat(DailyLucky.of(ME, d)).isEqualTo(DailyLucky.of(ME, d));

        long distinct = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> DailyLucky.of(ME, d.plusDays(i)).item()).distinct().count();
        assertThat(distinct).isGreaterThan(5); // 한 달 동안 아이템이 계속 바뀐다
    }

    @Test
    void differentPeopleSameDayDiffer() {
        LocalDate d = LocalDate.of(2026, 9, 29);
        DailyLucky a = DailyLucky.of(ME, d);
        DailyLucky b = DailyLucky.of(new SajuPillars("경진", "기축", "무술", "임자"), d);
        assertThat(a.element() != b.element() || !a.item().equals(b.item())).isTrue();
    }

    @Test
    void elementUsesTodayPillar() {
        // 팔자 임오 계묘 신사 을미: 수2 화2 목3 금1 토1 → 부족 토(동률 금·토 중 앞). 오늘 일진이 토 2글자(무진)면 금이 부족해진다
        assertThat(Element.lacking(ME)).isEqualTo(Element.EARTH);
        assertThat(Element.lacking(ME, "무진")).isEqualTo(Element.METAL);
    }
}
