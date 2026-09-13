package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingScorerTest {

    private final ReadingScorer scorer = new ReadingScorer();

    @Test
    void sameInputSameScores() {
        SajuPillars p = new SajuPillars("임오", "계묘", "신사", "을미");
        assertThat(scorer.score(p)).isEqualTo(scorer.score(p));
        assertThat(scorer.score(p)).containsOnlyKeys(ReadingCategory.MARRIAGE, ReadingCategory.CHILDREN, ReadingCategory.LOVE);
    }

    @Test
    void worksWithoutHourPillar() {
        Map<ReadingCategory, Integer> g = scorer.score(new SajuPillars("경진", "기축", "무술", null));
        assertThat(g).hasSize(3);
        assertThat(g.values()).allMatch(v -> v >= 0 && v <= 100);
    }

    @Test
    void spouseBranchRelationMovesMarriageScore() {
        // 일간 갑(목): 일지 진(토) = 재성 → 보너스 30. 일지 인(목) = 비겁 → 보너스 0
        int jae = scorer.score(new SajuPillars("임오", "계묘", "갑진", "신미")).get(ReadingCategory.MARRIAGE);
        int bi = scorer.score(new SajuPillars("임오", "계묘", "갑인", "신미")).get(ReadingCategory.MARRIAGE);
        assertThat(jae).isGreaterThan(bi);
    }

    @Test
    void gradeBoundaries() {
        assertThat(Grade.of(100)).isEqualTo(Grade.SS);
        assertThat(Grade.of(94)).isEqualTo(Grade.SS);
        assertThat(Grade.of(93)).isEqualTo(Grade.S);
        assertThat(Grade.of(84)).isEqualTo(Grade.S);
        assertThat(Grade.of(83)).isEqualTo(Grade.A_PLUS);
        assertThat(Grade.of(74)).isEqualTo(Grade.A_PLUS);
        assertThat(Grade.of(73)).isEqualTo(Grade.A);
        assertThat(Grade.of(64)).isEqualTo(Grade.A);
        assertThat(Grade.of(63)).isEqualTo(Grade.B_PLUS);
        assertThat(Grade.of(52)).isEqualTo(Grade.B_PLUS);
        assertThat(Grade.of(51)).isEqualTo(Grade.B);
        assertThat(Grade.of(0)).isEqualTo(Grade.B);
    }
}
