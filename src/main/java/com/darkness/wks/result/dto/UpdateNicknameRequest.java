package com.darkness.wks.result.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 변경 요청")
public record UpdateNicknameRequest(
        @Schema(description = "새 닉네임", example = "도윤")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 8, message = "닉네임은 8자 이하여야 합니다.")
        String nickname
) {
}
