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
    void elementChangesEveryTwoDaysItemEveryDay() {
        // 일간 신(금). 2000-01-01 무오(토)=인성일 → 금, 01-02 기미(토) → 금, 01-03 경신(금)=비겁일 → 수
        LocalDate d = LocalDate.of(2000, 1, 1);
        assertThat(DailyLucky.of(ME, d).element()).isEqualTo(Element.METAL);
        assertThat(DailyLucky.of(ME, d.plusDays(1)).element()).isEqualTo(Element.METAL);
        assertThat(DailyLucky.of(ME, d.plusDays(2)).element()).isEqualTo(Element.WATER);
        assertThat(DailyLucky.of(ME, d).item()).isNotEqualTo(DailyLucky.of(ME, d.plusDays(1)).item());
    }
}
