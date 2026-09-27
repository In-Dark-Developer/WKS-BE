package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuckyPoolTest {

    @Test
    void lineWithoutEqualsNamesFileAndLine() {
        assertThatThrownBy(() -> LuckyPool.readKeyValues("lucky/broken-no-equals.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lucky/broken-no-equals.txt:3");
    }

    @Test
    void unknownElementKeyNamesTheKey() {
        assertThatThrownBy(() -> LuckyPool.load("lucky/broken-key.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TREE");
    }
}
