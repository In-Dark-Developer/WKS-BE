package com.darkness.wks.saju;

import com.darkness.wks.common.Gender;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingScorerTest {

    private final ReadingScorer scorer = new ReadingScorer();

    @Test
    void sameInputSameScores() {
        SajuPillars p = new SajuPillars("임오", "계묘", "신사", "을미");
        assertThat(scorer.score(p, Gender.MALE)).isEqualTo(scorer.score(p, Gender.MALE));
        assertThat(scorer.score(p, Gender.MALE)).containsOnlyKeys(ReadingCategory.MARRIAGE, ReadingCategory.CHILDREN, ReadingCategory.LOVE);
    }

    @Test
    void worksWithoutHourPillar() {
        Map<ReadingCategory, Integer> g = scorer.score(new SajuPillars("경진", "기축", "무술", null), Gender.FEMALE);
        assertThat(g).hasSize(3);
        assertThat(g.values()).allMatch(v -> v >= 0 && v <= 100);
    }

    @Test
    void spouseBranchRelationMovesMarriageScore() {
        // 일간 갑(목): 일지 진(토) = 재성 → 남자 배우자성 보너스 30. 일지 인(목) = 비겁 → 보너스 0
        int jae = scorer.score(new SajuPillars("임오", "계묘", "갑진", "신미"), Gender.MALE).get(ReadingCategory.MARRIAGE);
        int bi = scorer.score(new SajuPillars("임오", "계묘", "갑인", "신미"), Gender.MALE).get(ReadingCategory.MARRIAGE);
        assertThat(jae).isGreaterThan(bi);
    }

    @Test
    void spouseStarDependsOnGender() {
        // 일간 갑(목), 일지 진(토)=재성: 남자에겐 배우자성 → 남자 결혼 점수가 더 높다
        SajuPillars p = new SajuPillars("임오", "계묘", "갑진", "신미");
        assertThat(scorer.score(p, Gender.MALE).get(ReadingCategory.MARRIAGE))
                .isGreaterThan(scorer.score(p, Gender.FEMALE).get(ReadingCategory.MARRIAGE));
        // 일지 신(금)=관성: 여자 배우자성
        SajuPillars q = new SajuPillars("임오", "계묘", "갑신", "신미");
        assertThat(scorer.score(q, Gender.FEMALE).get(ReadingCategory.MARRIAGE))
                .isGreaterThan(scorer.score(q, Gender.MALE).get(ReadingCategory.MARRIAGE));
    }

    @Test
    void childStarDependsOnGender() {
        // 일간 갑(목). 시주 병오(화·화)=식상: 여자 자녀성. 시주 경신(금·금)=관성: 남자 자녀성
        SajuPillars sik = new SajuPillars("임오", "계묘", "갑진", "병오");
        SajuPillars gwan = new SajuPillars("임오", "계묘", "갑진", "경신");
        assertThat(scorer.score(sik, Gender.FEMALE).get(ReadingCategory.CHILDREN))
                .isGreaterThan(scorer.score(sik, Gender.MALE).get(ReadingCategory.CHILDREN));
        assertThat(scorer.score(gwan, Gender.MALE).get(ReadingCategory.CHILDREN))
                .isGreaterThan(scorer.score(gwan, Gender.FEMALE).get(ReadingCategory.CHILDREN));
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
