package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DestinyTitleTest {

    @Test
    void levelSplitsAtAPlus() {
        assertThat(DestinyTitle.level(74)).isEqualTo("상"); // A+
        assertThat(DestinyTitle.level(73)).isEqualTo("하"); // A
        assertThat(DestinyTitle.level(100)).isEqualTo("상");
        assertThat(DestinyTitle.level(0)).isEqualTo("하");
    }

    @Test
    void keyOrderIsLoveMarriageChildren() {
        // 연애 상 / 결혼 하 / 자녀 하 = 유형 4
        assertThat(DestinyTitle.of(60, 60, 80)).isEqualTo("사랑이 깊은 운명");
        // 연애 하 / 결혼 상 / 자녀 상 = 유형 5
        assertThat(DestinyTitle.of(80, 80, 60)).isEqualTo("가정을 이룰 운명");
    }

    @Test
    void eightCombinationsAllHaveTitles() {
        java.util.Set<String> titles = new java.util.HashSet<>();
        for (int m : new int[]{80, 60}) for (int c : new int[]{80, 60}) for (int l : new int[]{80, 60}) {
            titles.add(DestinyTitle.of(m, c, l));
        }
        assertThat(titles).hasSize(8).doesNotContainNull();
    }
}
