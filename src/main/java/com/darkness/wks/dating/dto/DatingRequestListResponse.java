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
                                                 DatingRecommendation recommendation,
                                                 String blurredPhotoUrl, String originalPhotoUrl) {
        boolean received = request.getRecipient().getMemberId().equals(viewerMemberId);
        DatingProfile other = received ? request.getSender() : request.getRecipient();
        DatingRequestResponse base = DatingRequestResponse.from(request, viewerMemberId);
        LockedField photo = field(received || recommendation.isPhotoUnlocked(), 10, originalPhotoUrl);
        LockedField name = field(received || recommendation.isNameUnlocked(), 7, other.getName());
        LockedField department = field(received || recommendation.isDepartmentUnlocked(), 5,
                other.getDepartment());
        Counterpart counterpart = new Counterpart(recommendation.getScore(), other.getMbti(), other.getBio(),
                blurredPhotoUrl, new ProfileFields(photo, name, department));
        return new DatingRequestListResponse(base.requestId(), base.candidateId(), base.status(),
                base.createdAt(), base.respondedAt(), base.contactMethod(), base.contactValue(), counterpart);
    }

    private static LockedField field(boolean visible, int cost, String value) {
        return visible ? LockedField.unlocked(value) : LockedField.locked(cost);
    }

    public record Counterpart(int score, String mbti, String bio,
                              String blurredPhotoUrl, ProfileFields fields) {
    }

    public record ProfileFields(LockedField photo, LockedField name, LockedField department) {
    }
}
