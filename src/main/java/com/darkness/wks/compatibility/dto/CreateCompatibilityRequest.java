package com.darkness.wks.compatibility.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "친구 궁합 생성 요청")
public record CreateCompatibilityRequest(
        @Schema(description = "친구가 POST /api/results로 생성한 UUID v4 결과 ID", format = "uuid")
        @NotBlank(message = "친구의 결과 ID는 필수입니다.")
        String guestResultId
) {
}
