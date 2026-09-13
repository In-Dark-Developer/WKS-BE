package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingScorerTest {

    private final ReadingScorer scorer = new ReadingScorer();

    @Test
    void sameInputSameGrades() {
        SajuPillars p = new SajuPillars("임오", "계묘", "신사", "을미");
        assertThat(scorer.score(p)).isEqualTo(scorer.score(p));
        assertThat(scorer.score(p)).containsOnlyKeys(ReadingCategory.MARRIAGE, ReadingCategory.CHILDREN, ReadingCategory.LOVE);
    }

    @Test
    void worksWithoutHourPillar() {
        Map<ReadingCategory, Grade> g = scorer.score(new SajuPillars("경진", "기축", "무술", null));
        assertThat(g).hasSize(3);
        assertThat(g.values()).doesNotContainNull();
    }

    @Test
    void spouseBranchRelationMovesMarriageGrade() {
        // 일간 갑(목): 일지 진(토) = 재성 → 보너스 25. 일지 인(목) = 비겁 → 보너스 5
        Grade jae = scorer.score(new SajuPillars("임오", "계묘", "갑진", "신미")).get(ReadingCategory.MARRIAGE);
        Grade bi = scorer.score(new SajuPillars("임오", "계묘", "갑인", "신미")).get(ReadingCategory.MARRIAGE);
        assertThat(jae.ordinal()).isLessThan(bi.ordinal()); // ordinal 작을수록 높은 등급
    }

    @Test
    void gradeBoundaries() {
        assertThat(Grade.of(100)).isEqualTo(Grade.SS);
        assertThat(Grade.of(88)).isEqualTo(Grade.SS);
        assertThat(Grade.of(87)).isEqualTo(Grade.S);
        assertThat(Grade.of(76)).isEqualTo(Grade.S);
        assertThat(Grade.of(75)).isEqualTo(Grade.A_PLUS);
        assertThat(Grade.of(64)).isEqualTo(Grade.A_PLUS);
        assertThat(Grade.of(63)).isEqualTo(Grade.A);
        assertThat(Grade.of(52)).isEqualTo(Grade.A);
        assertThat(Grade.of(51)).isEqualTo(Grade.B_PLUS);
        assertThat(Grade.of(40)).isEqualTo(Grade.B_PLUS);
        assertThat(Grade.of(39)).isEqualTo(Grade.B);
        assertThat(Grade.of(0)).isEqualTo(Grade.B);
    }
}
