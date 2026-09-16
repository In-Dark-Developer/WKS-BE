package com.darkness.wks.signup.dto;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "소개팅 사전등록 신청 요청")
public record CreateSignupRequest(
        @Schema(description = "학교 웹메일", example = "dev@dgu.ac.kr")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "직접 생성한 사주 결과 UUID v4 ID. 사주 없이 신청하면 null", example = "3f2a9c1e-....", nullable = true)
        String resultId,

        @Schema(description = "성별", example = "MALE")
        @NotNull(message = "성별은 필수입니다.")
        Gender gender,

        @Schema(description = "선호 성별", example = "FEMALE")
        @NotNull(message = "선호 성별은 필수입니다.")
        Gender preferGender,

        // ⚠️ 아래 6개 필드는 필수/선택 여부가 기획 미확정 상태(2026-09-15 기준) — 전부 nullable로 받는다.
        // 값이 오면 형식만 검증한다. 결정되면 여기에 @NotBlank/@NotNull 추가

        @Schema(description = "이름", example = "김동국", nullable = true)
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Schema(description = "연락 수단", example = "PHONE", nullable = true)
        ContactMethod contactMethod,

        @Schema(description = "연락처 값. contactMethod가 PHONE이면 전화번호, INSTAGRAM이면 계정 아이디", example = "010-1234-5678", nullable = true)
        @Size(max = 100, message = "연락처는 100자 이하여야 합니다.")
        String contactValue,

        @Schema(description = "학과", example = "컴퓨터공학과", nullable = true)
        @Size(max = 100, message = "학과는 100자 이하여야 합니다.")
        String department,

        @Schema(description = "MBTI", example = "INFP", nullable = true)
        @Pattern(regexp = "^$|^[EI][SN][TF][JP]$", message = "MBTI 형식이 올바르지 않습니다.")
        String mbti,

        @Schema(description = "자기소개", example = "축제를 좋아하는 컴공생입니다.", nullable = true)
        @Size(max = 500, message = "자기소개는 500자 이하여야 합니다.")
        String bio,

        @Schema(description = "`POST /api/signups/photo-upload-url` 로 발급받아 S3에 업로드한 사진의 key. 선택값", example = "signup-photos/3f2a9c1e-....jpg", nullable = true)
        @Size(max = 255, message = "photoKey는 255자 이하여야 합니다.")
        String photoKey
) {
}
