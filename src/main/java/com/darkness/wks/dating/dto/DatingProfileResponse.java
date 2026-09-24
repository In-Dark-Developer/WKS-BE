package com.darkness.wks.dating.dto;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.dating.entity.DatingProfile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record DatingProfileResponse(
        @Schema(description = "내 소개팅 프로필 ID. 다른 사람의 추천 카드에는 candidateId로 표시",
                example = "3f2a9c1e-0000-4000-8000-000000000001") UUID candidateId,
        String email,
        boolean emailVerified,
        String name,
        ContactMethod contactMethod,
        String contactValue,
        String department,
        String mbti,
        String bio,
        @Schema(description = "이 프로필에 연결된 사진 ID. 사진 업로드 URL 발급 응답에서 받은 값",
                example = "f1c4832a-0000-4000-8000-000000000002") UUID photoId
) {
    public static DatingProfileResponse from(DatingProfile profile) {
        return new DatingProfileResponse(profile.getId(), profile.getEmail(),
                profile.getVerifiedAt() != null, profile.getName(), profile.getContactMethod(),
                profile.getContactValue(), profile.getDepartment(), profile.getMbti(),
                profile.getBio(), profile.getPhoto().getId());
    }
}
