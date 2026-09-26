package com.darkness.wks.member.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkResultRequest(@NotBlank String resultId) {
}
