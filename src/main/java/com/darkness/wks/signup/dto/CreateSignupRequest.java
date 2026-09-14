package com.darkness.wks.signup.dto;

import com.darkness.wks.common.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        Gender preferGender
) {
}
