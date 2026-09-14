package com.darkness.wks.signup.dto;

import com.darkness.wks.signup.entity.Signup;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소개팅 사전등록 신청 결과")
public record SignupResponse(
        @Schema(example = "1024") Long signupId,
        @Schema(example = "true") boolean couponIssued,
        @Schema(example = "true") boolean mailSent,
        @Schema(example = "신청이 접수됐다. 인증 메일을 확인해라.") String message
) {

    public static SignupResponse of(Signup signup, boolean mailSent) {
        String message = mailSent
                ? "신청이 접수됐다. 인증 메일을 확인해라."
                : "신청이 접수됐다. 인증 메일 발송에 실패했으니 재발송을 요청해라.";
        return new SignupResponse(signup.getId(), signup.isCouponIssued(), mailSent, message);
    }
}
