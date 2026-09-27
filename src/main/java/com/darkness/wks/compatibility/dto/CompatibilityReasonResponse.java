package com.darkness.wks.compatibility.dto;

import com.darkness.wks.compatibility.entity.Compatibility;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "궁합 상세 이유. 두 사람이 같은 내용을 본다")
public record CompatibilityReasonResponse(
        @Schema(description = "왜 이런 인연일까요?", example = "나무의 기운이 불의 기운을 살리는 사이라 ...") String why,
        @Schema(description = "둘이 만나게 된다면?", example = "함께 있으면 나무의 기운을 가진 분이 먼저 ...") String together,
        @Schema(description = "둘이 싸우게 된다면?", example = "불의 기운 쪽이 먼저 달아오르기 쉬워요 ...") String conflict
) {

    public static CompatibilityReasonResponse from(Compatibility c) {
        return new CompatibilityReasonResponse(c.getReasonWhy(), c.getReasonTogether(), c.getReasonConflict());
    }
}
