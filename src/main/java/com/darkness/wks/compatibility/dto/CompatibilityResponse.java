package com.darkness.wks.compatibility.dto;

import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.result.entity.Result;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "친구 궁합 결과")
public record CompatibilityResponse(
        @Schema(minimum = "0", maximum = "100", example = "92") int score,
        @Schema(example = "GUIIN") CompatibilityTier tier,
        @Schema(example = "서연") String originNickname,
        @Schema(example = "민수") String guestNickname
) {

    public static CompatibilityResponse from(Compatibility compatibility, Result origin, Result guest) {
        return new CompatibilityResponse(
                compatibility.getScore(),
                compatibility.getTier(),
                origin.getNickname(),
                guest.getNickname()
        );
    }
}
