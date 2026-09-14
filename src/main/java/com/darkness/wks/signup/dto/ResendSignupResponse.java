package com.darkness.wks.signup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증 메일 재발송 결과")
public record ResendSignupResponse(
        @Schema(example = "true") boolean mailSent,
        @Schema(example = "인증 메일을 재발송했다.") String message
) {

    public static ResendSignupResponse of(boolean mailSent) {
        String message = mailSent
                ? "인증 메일을 재발송했다."
                : "인증 메일 발송에 실패했다. 잠시 후 다시 시도해라.";
        return new ResendSignupResponse(mailSent, message);
    }
}
