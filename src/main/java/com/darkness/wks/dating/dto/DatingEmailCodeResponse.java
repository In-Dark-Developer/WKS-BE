package com.darkness.wks.dating.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record DatingEmailCodeResponse(
        @Schema(description = "코드 만료 시각. 지나면 새로 발송받아야 한다") Instant expiresAt,
        @Schema(description = "재발송 버튼을 다시 켤 수 있는 시각") Instant resendAvailableAt
) {
}
