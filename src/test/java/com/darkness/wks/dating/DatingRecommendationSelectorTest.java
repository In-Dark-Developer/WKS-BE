package com.darkness.wks.dating;

import com.darkness.wks.common.Gender;
import com.darkness.wks.compatibility.CompatibilityCalculator;
import com.darkness.wks.saju.SajuPillars;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DatingRecommendationSelectorTest {

    private final DatingRecommendationSelector selector =
            new DatingRecommendationSelector(new CompatibilityCalculator());

    @Test
    void recommendsOnlyUnseenAvailableOppositeGenderCandidates() {
        UUID viewerId = UUID.randomUUID();
        UUID shownId = UUID.randomUUID();
        UUID matchedId = UUID.randomUUID();
        UUID sameGenderId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        SajuPillars pillars = new SajuPillars("갑자", "을축", "병인", null);

        var result = selector.select(viewerId, Gender.MALE, pillars, List.of(
                candidate(viewerId, Gender.MALE, pillars, true),
                candidate(shownId, Gender.FEMALE, pillars, true),
                candidate(matchedId, Gender.FEMALE, pillars, false),
                candidate(sameGenderId, Gender.MALE, pillars, true),
                candidate(newId, Gender.FEMALE, pillars, true)
        ), Set.of(shownId), 3);

        assertThat(result).extracting(DatingRecommendationSelector.RankedCandidate::profileId)
                .containsExactly(newId);
    }

    @Test
    void limitsResultsAndSortsByCompatibilityScore() {
        UUID viewerId = UUID.randomUUID();
        SajuPillars viewer = new SajuPillars("갑자", "을축", "병인", "정묘");
        var candidates = List.of(
                candidate(UUID.randomUUID(), Gender.FEMALE,
                        new SajuPillars("계해", "임술", "신유", "경신"), true),
                candidate(UUID.randomUUID(), Gender.FEMALE, viewer, true),
                candidate(UUID.randomUUID(), Gender.FEMALE,
                        new SajuPillars("무오", "기미", "경신", "신유"), true)
        );

        var result = selector.select(viewerId, Gender.MALE, viewer, candidates, Set.of(), 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).score()).isGreaterThanOrEqualTo(result.get(1).score());
    }

    private static DatingRecommendationSelector.Candidate candidate(
            UUID id, Gender gender, SajuPillars pillars, boolean available) {
        return new DatingRecommendationSelector.Candidate(id, gender, pillars, available);
    }
}
