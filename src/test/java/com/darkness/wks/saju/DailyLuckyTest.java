package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyLuckyTest {

    private static final SajuPillars ME = new SajuPillars("임오", "계묘", "신사", "을미"); // 일간 신(금), 일지 사(화)

    @Test
    void todayPillar() {
        assertThat(DailyLucky.todayPillar(LocalDate.of(2000, 1, 1))).isEqualTo("무오");
    }

    @Test
    void activeElementThatHelpsMeWins() {
        // 일진 무진(토·토): 토는 나(금)의 인성(궁합 최고) + 오늘 천간·지지 모두 토(활성 최고) → 토
        assertThat(DailyLucky.luckyElement(ME, "무진")).isEqualTo(Element.EARTH);
        // 일진 경신(금·금): 금은 비겁(4) + 활성 3.5. 토는 인성(5)이지만 오늘 활성 0 → 활성 60% 가중으로 금
        assertThat(DailyLucky.luckyElement(ME, "경신")).isEqualTo(Element.METAL);
        // 일진 병오(화·화): 화는 관성(1)이라 궁합 최저. 화가 생하는 토(인성)가 활성 +2 → 토
        assertThat(DailyLucky.luckyElement(ME, "병오")).isEqualTo(Element.EARTH);
    }

    @Test
    void sameUserSameDaySameItemDifferentDayMayDiffer() {
        LocalDate d = LocalDate.of(2026, 9, 29);
        assertThat(DailyLucky.of(ME, d, "user-1")).isEqualTo(DailyLucky.of(ME, d, "user-1"));
        long distinct = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> DailyLucky.of(ME, d.plusDays(i), "user-1").item()).distinct().count();
        assertThat(distinct).isGreaterThan(5);
    }

    @Test
    void differentUsersSameDayCanGetDifferentItems() {
        LocalDate d = LocalDate.of(2026, 9, 29);
        long distinct = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> DailyLucky.of(ME, d, "user-" + i).item()).distinct().count();
        assertThat(distinct).isGreaterThan(3); // 같은 팔자·같은 날이어도 식별값으로 흩어진다
    }
}
