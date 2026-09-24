package com.darkness.wks.dating;

import com.darkness.wks.common.Gender;
import com.darkness.wks.compatibility.CompatibilityCalculator;
import com.darkness.wks.saju.SajuPillars;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 소개팅 후보의 점수만 계산한다. 친구 궁합 기록은 만들지 않는다. */
@Component
public class DatingRecommendationSelector {

    private final CompatibilityCalculator compatibilityCalculator;

    public DatingRecommendationSelector(CompatibilityCalculator compatibilityCalculator) {
        this.compatibilityCalculator = compatibilityCalculator;
    }

    public List<RankedCandidate> select(UUID viewerProfileId, Gender viewerGender, SajuPillars viewerPillars,
                                         List<Candidate> candidates, Set<UUID> previouslyShownIds, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        return candidates.stream()
                .filter(candidate -> !candidate.profileId().equals(viewerProfileId))
                .filter(candidate -> candidate.gender() != viewerGender)
                .filter(Candidate::available)
                .filter(candidate -> !previouslyShownIds.contains(candidate.profileId()))
                .map(candidate -> new RankedCandidate(candidate.profileId(),
                        compatibilityCalculator.calculate(viewerPillars, candidate.pillars())))
                .sorted(Comparator.comparingInt(RankedCandidate::score).reversed()
                        .thenComparing(RankedCandidate::profileId))
                .limit(limit)
                .toList();
    }

    public record Candidate(UUID profileId, Gender gender, SajuPillars pillars, boolean available) {
    }

    public record RankedCandidate(UUID profileId, int score) {
    }
}
