package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElementTest {

    @Test
    void mapsStemsAndBranches() {
        assertThat(Element.ofStem('갑')).isEqualTo(Element.WOOD);
        assertThat(Element.ofStem('계')).isEqualTo(Element.WATER);
        assertThat(Element.ofBranch('자')).isEqualTo(Element.WATER);
        assertThat(Element.ofBranch('축')).isEqualTo(Element.EARTH);
        assertThat(Element.ofBranch('유')).isEqualTo(Element.METAL);
    }

    @Test
    void lackingIsLeastFrequentElement() {
        // 임오 계묘 신사 을미: 수2 화2 목3 금1 토1 → 동률(금·토) 중 순서 앞선 토
        assertThat(Element.lacking(new SajuPillars("임오", "계묘", "신사", "을미"))).isEqualTo(Element.EARTH);
        // 갑인 을묘 병오 정사: 목4 화4, 토·금·수 0 → 토
        assertThat(Element.lacking(new SajuPillars("갑인", "을묘", "병오", "정사"))).isEqualTo(Element.EARTH);
        // 시주 없음: 경진 기축 무술 → 금1 토5 → 목(0)
        assertThat(Element.lacking(new SajuPillars("경진", "기축", "무술", null))).isEqualTo(Element.WOOD);
    }
}
