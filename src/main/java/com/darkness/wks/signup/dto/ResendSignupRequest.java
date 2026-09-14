package com.darkness.wks.signup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "인증 메일 재발송 요청")
public record ResendSignupRequest(
        @Schema(description = "재발송받을 이메일", example = "dev@dgu.ac.kr")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {
}
