package com.darkness.wks.admin.dto;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.dating.entity.DatingProfile;

import java.time.Instant;
import java.util.UUID;

/** 운영자용이라 잠긴 필드 없이 전부 내린다. 프론트 계약(api-spec)이 아니다. */
public record AdminDatingProfileResponse(
        UUID profileId,
        Long memberId,
        String email,
        String name,
        ContactMethod contactMethod,
        String contactValue,
        String department,
        String mbti,
        String bio,
        UUID photoId,
        String photoUrl,
        Instant verifiedAt,
        Instant deactivatedAt,
        Instant createdAt
) {
    public static AdminDatingProfileResponse from(DatingProfile profile, String photoUrl) {
        return new AdminDatingProfileResponse(profile.getId(), profile.getMemberId(), profile.getEmail(),
                profile.getName(), profile.getContactMethod(), profile.getContactValue(), profile.getDepartment(),
                profile.getMbti(), profile.getBio(), profile.getPhoto().getId(), photoUrl,
                profile.getVerifiedAt(), profile.getDeactivatedAt(), profile.getCreatedAt());
    }
}
