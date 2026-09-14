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
    void generatesAndControlsFollowTheCycle() {
        assertThat(Element.WOOD.generates(Element.FIRE)).isTrue();   // 목생화
        assertThat(Element.WATER.generates(Element.WOOD)).isTrue();  // 수생목
        assertThat(Element.WOOD.controls(Element.EARTH)).isTrue();   // 목극토
        assertThat(Element.METAL.controls(Element.WOOD)).isTrue();   // 금극목
        assertThat(Element.WOOD.controls(Element.FIRE)).isFalse();
    }

    @Test
    void roleForDayMaster() {
        Element me = Element.METAL; // 일간 신
        assertThat(Element.METAL.roleFor(me)).isEqualTo(0); // 비겁
        assertThat(Element.WATER.roleFor(me)).isEqualTo(1); // 식상
        assertThat(Element.WOOD.roleFor(me)).isEqualTo(2);  // 재성
        assertThat(Element.FIRE.roleFor(me)).isEqualTo(3);  // 관성
        assertThat(Element.EARTH.roleFor(me)).isEqualTo(4); // 인성
    }

    @Test
    void strengthsWeightPillarsAndBranches() {
        // 갑인 갑인 갑인 갑인: 전부 목 → 목 = (0.7+1.3+1.0+0.9) × (1 + 0.85)
        double[] s = Element.strengths(new SajuPillars("갑인", "갑인", "갑인", "갑인"));
        assertThat(s[Element.WOOD.ordinal()]).isCloseTo(3.9 * 1.85, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(s[Element.FIRE.ordinal()]).isZero();
        // 시주 없으면 3주만
        double[] t = Element.strengths(new SajuPillars("갑인", "갑인", "갑인", null));
        assertThat(t[Element.WOOD.ordinal()]).isCloseTo(3.0 * 1.85, org.assertj.core.data.Offset.offset(1e-9));
    }
}
