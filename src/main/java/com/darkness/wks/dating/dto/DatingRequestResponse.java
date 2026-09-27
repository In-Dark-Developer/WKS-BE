package com.darkness.wks.dating.dto;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.dating.entity.DatingRequest;
import com.darkness.wks.dating.entity.DatingRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record DatingRequestResponse(
        UUID requestId,
        UUID candidateId,
        DatingRequestStatus status,
        Instant createdAt,
        Instant respondedAt,
        ContactMethod contactMethod,
        String contactValue
) {
    public static DatingRequestResponse from(DatingRequest request, Long viewerMemberId) {
        DatingProfile other = request.getSender().getMemberId().equals(viewerMemberId)
                ? request.getRecipient() : request.getSender();
        boolean accepted = request.getStatus() == DatingRequestStatus.ACCEPTED;
        return new DatingRequestResponse(request.getId(), other.getId(), request.getStatus(),
                request.getCreatedAt(), request.getRespondedAt(),
                accepted ? other.getContactMethod() : null,
                accepted ? other.getContactValue() : null);
    }
}
