package com.darkness.wks.dating.dto;

import com.darkness.wks.dating.entity.DatingRecommendation;

import java.util.List;
import java.util.UUID;

/**
 * @param rerollCost 지금 리롤하면 드는 실. 오늘(KST) 무료 리롤이 남았으면 0 — 프론트가 버튼에 "무료"/"5실"을
 *                   표시하는 데 쓴다
 */
public record DatingRecommendationResponse(List<CandidateCard> candidates, int rerollCost) {

    public record CandidateCard(int rank, UUID candidateId, int score,
                                String mbti, String bio, String blurredPhotoUrl, CandidateFields fields) {
        /**
         * @param unlockedPhotoUrl 사진이 해금됐을 때만 원본 서명 URL. 잠긴 상태에서는 절대 호출부에서
         *                         계산·전달하지 않는다(FR-DT-04) — {@code null} 이면 잠긴 것으로 본다
         */
        public static CandidateCard from(int rank, DatingRecommendation recommendation, String blurredPhotoUrl,
                                          String unlockedPhotoUrl) {
            var profile = recommendation.getCandidate();
            CandidateFields fields = new CandidateFields(
                    field(recommendation.isPhotoUnlocked(), 10, unlockedPhotoUrl),
                    field(recommendation.isNameUnlocked(), 7, profile.getName()),
                    field(recommendation.isDepartmentUnlocked(), 5, profile.getDepartment()),
                    field(recommendation.isReasonUnlocked(), 3, recommendation.getReasonContent()));
            return new CandidateCard(rank, profile.getId(), recommendation.getScore(),
                    profile.getMbti(), profile.getBio(), blurredPhotoUrl, fields);
        }

        private static LockedField field(boolean unlocked, int cost, String value) {
            return unlocked ? LockedField.unlocked(value) : LockedField.locked(cost);
        }
    }

    public record CandidateFields(LockedField photo, LockedField name,
                                  LockedField department, LockedField reason) {
    }

    /**
     * 잠긴 상태에서는 {@code cost} 만, 해금 후에는 {@code value} 만 채운다 — 잠긴 필드는 값 자체를
     * 응답에 넣지 않는다(AGENTS.md, FR-DT-03). {@code value} 가 {@code null} 이면 해금은 됐지만 아직
     * 값이 준비 안 된 것이다(예: 궁합 까닭 생성이 지연·실패한 직후) — 해금 API를 다시 부르면 된다.
     */
    public record LockedField(boolean locked, Integer cost, String value) {
        public static LockedField locked(int cost) {
            return new LockedField(true, cost, null);
        }

        public static LockedField unlocked(String value) {
            return new LockedField(false, null, value);
        }
    }
}
