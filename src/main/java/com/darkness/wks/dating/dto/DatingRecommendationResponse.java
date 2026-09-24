package com.darkness.wks.dating.dto;

import com.darkness.wks.dating.entity.DatingRecommendation;

import java.util.List;
import java.util.UUID;

public record DatingRecommendationResponse(List<CandidateCard> candidates) {

    public record CandidateCard(int rank, UUID candidateId, int score,
                                String mbti, String bio, CandidateFields fields) {
        public static CandidateCard from(int rank, DatingRecommendation recommendation) {
            var profile = recommendation.getCandidate();
            return new CandidateCard(rank, profile.getId(), recommendation.getScore(),
                    profile.getMbti(), profile.getBio(), new CandidateFields(
                    locked(10), locked(7), locked(5), locked(3)));
        }

        private static LockedField locked(int cost) {
            return new LockedField(true, cost);
        }
    }

    public record CandidateFields(LockedField photo, LockedField name,
                                  LockedField department, LockedField reason) {
    }

    public record LockedField(boolean locked, int cost) {
    }
}
