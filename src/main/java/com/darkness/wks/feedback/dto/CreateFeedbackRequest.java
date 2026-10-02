package com.darkness.wks.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFeedbackRequest(
        @Schema(description = "자유 의견 (앞뒤 공백 제거 후 최대 2,000자)")
        @NotBlank(message = "피드백 내용을 입력해 주세요.")
        @Size(max = 2000, message = "피드백은 2,000자 이하로 입력해 주세요.")
        String content
) {
    public CreateFeedbackRequest {
        content = content == null ? null : content.strip();
    }
}
