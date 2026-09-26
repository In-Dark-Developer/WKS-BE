package com.darkness.wks.dating.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DatingEmailCodeVerifyRequest(
        @NotBlank @Email @Schema(example = "student@dgu.ac.kr") String email,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") @Schema(example = "123456") String code
) {
}
