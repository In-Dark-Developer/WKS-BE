package com.darkness.wks.dating.dto;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.dating.dto.DatingRecommendationResponse.LockedField;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.dating.entity.DatingRecommendation;
import com.darkness.wks.dating.entity.DatingRequest;
import com.darkness.wks.dating.entity.DatingRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record DatingRequestListResponse(
        UUID requestId,
        UUID candidateId,
        DatingRequestStatus status,
        Instant createdAt,
        Instant respondedAt,
        ContactMethod contactMethod,
        String contactValue,
        Counterpart counterpart
) {
    public static DatingRequestListResponse from(DatingRequest request, Long viewerMemberId,
                                                 DatingRecommendation recommendation, String age,
                                                 String blurredPhotoUrl, String originalPhotoUrl) {
        boolean received = request.getRecipient().getMemberId().equals(viewerMemberId);
        DatingProfile other = received ? request.getSender() : request.getRecipient();
        DatingRequestResponse base = DatingRequestResponse.from(request, viewerMemberId);
        LockedField photo = field(received || recommendation.isPhotoUnlocked(), 10, originalPhotoUrl);
        LockedField name = field(received || recommendation.isNameUnlocked(), 7, other.getName());
        LockedField department = field(received || recommendation.isDepartmentUnlocked(), 5,
                other.getDepartment());
        // 받은 목록은 받은 사람 시점 문장(요청 행에 캐시, 무료). 보낸 목록은 카드와 같은 해금 상태·문장 (#123)
        LockedField reason = received ? LockedField.unlocked(request.getRecipientReason())
                : field(recommendation.isReasonUnlocked(), 3, recommendation.getReasonContent());
        Counterpart counterpart = new Counterpart(recommendation.getScore(), age, other.getMbti(), other.getBio(),
                blurredPhotoUrl, new ProfileFields(photo, name, department, reason));
        return new DatingRequestListResponse(base.requestId(), base.candidateId(), base.status(),
                base.createdAt(), base.respondedAt(), base.contactMethod(), base.contactValue(), counterpart);
    }

    private static LockedField field(boolean visible, int cost, String value) {
        return visible ? LockedField.unlocked(value) : LockedField.locked(cost);
    }

    /** @param age 추천 카드와 같은 기본 공개 나이 표기(예: "00년생"). 결과가 연결되지 않았으면 {@code null} */
    public record Counterpart(int score, String age, String mbti, String bio,
                              String blurredPhotoUrl, ProfileFields fields) {
    }

    /** @param reason 받은 목록에서 {@code value} 가 {@code null} 이면 아직 생성 전이다 — 잠시 후 다시 조회하면 된다 */
    public record ProfileFields(LockedField photo, LockedField name, LockedField department, LockedField reason) {
    }
}
