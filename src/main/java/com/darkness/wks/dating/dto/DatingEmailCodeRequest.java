package com.darkness.wks.dating.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DatingEmailCodeRequest(
        @NotBlank @Email @Schema(example = "student@dgu.ac.kr") String email
) {
}
