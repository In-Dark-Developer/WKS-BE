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
    void luckyFollowsTenGodRelation() {
        Element me = Element.METAL; // 일간 신
        assertThat(me.luckyAgainst(Element.WOOD)).isEqualTo(Element.FIRE);   // 재성일 → 관성
        assertThat(me.luckyAgainst(Element.FIRE)).isEqualTo(Element.EARTH);  // 관성일 → 인성
        assertThat(me.luckyAgainst(Element.EARTH)).isEqualTo(Element.METAL); // 인성일 → 비겁
        assertThat(me.luckyAgainst(Element.METAL)).isEqualTo(Element.WATER); // 비겁일 → 식상
        assertThat(me.luckyAgainst(Element.WATER)).isEqualTo(Element.WOOD);  // 식상일 → 재성
    }
}
