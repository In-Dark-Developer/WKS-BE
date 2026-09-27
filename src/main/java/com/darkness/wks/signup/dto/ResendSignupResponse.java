package com.darkness.wks.signup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메일 재발송 결과 (사주 결과가 있으면 매직링크, 없으면 인증 메일)")
public record ResendSignupResponse(
        @Schema(example = "true") boolean mailSent,
        @Schema(example = "메일을 다시 보냈어요.") String message
) {

    public static ResendSignupResponse of(boolean mailSent) {
        String message = mailSent
                ? "메일을 다시 보냈어요."
                : "메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요.";
        return new ResendSignupResponse(mailSent, message);
    }
}
