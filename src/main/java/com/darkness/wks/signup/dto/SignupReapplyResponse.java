package com.darkness.wks.signup.dto;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.signup.entity.Signup;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 재신청 폼 자동 채움. 사전신청 때 받지 않았던 항목(사진)과 당시 선택값이라 비어 있는 항목은 {@code null}
 * 로 내려가고, 프론트가 화면에서 받아 소개팅 프로필 등록 요청에 채워 넣는다.
 */
public record SignupReapplyResponse(
        @Schema(description = "사전신청 때 인증한 학교 이메일. 소개팅 프로필 등록 시 이 값을 그대로 보내야 한다",
                example = "student@dgu.ac.kr") String email,
        @Schema(description = "사전신청 때 연결된 사주 결과. POST /api/auth/kakao 의 resultId 로 보내면 계정에 연결된다",
                example = "3f2a9c1e-0000-4000-8000-000000000001") UUID resultId,
        String name,
        ContactMethod contactMethod,
        String contactValue,
        String department,
        String mbti,
        String bio
) {
    public static SignupReapplyResponse from(Signup signup) {
        return new SignupReapplyResponse(signup.getEmail(),
                signup.getResult() == null ? null : signup.getResult().getId(),
                signup.getName(), signup.getContactMethod(), signup.getContactValue(),
                signup.getDepartment(), signup.getMbti(), signup.getBio());
    }
}
