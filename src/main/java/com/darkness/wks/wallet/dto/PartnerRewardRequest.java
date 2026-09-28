package com.darkness.wks.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/wallet/partner-rewards 요청. api-spec.md §12 */
@Schema(description = "제휴처 유입 보상 요청")
public record PartnerRewardRequest(
        @Schema(description = "제휴처 링크의 ref 코드", example = "FESTIVAL")
        @NotBlank(message = "ref 는 필수입니다.")
        @Size(max = 100, message = "ref 는 100자 이하여야 합니다.")
        String ref
) {
}
