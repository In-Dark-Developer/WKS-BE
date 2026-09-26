package com.darkness.wks.dating.dto;

import com.darkness.wks.common.ContactMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DatingProfileRequest(
        @NotBlank @Email @Schema(example = "student@dgu.ac.kr") String email,
        @NotBlank @Size(max = 50) String name,
        @NotNull ContactMethod contactMethod,
        @NotBlank @Size(max = 100)
        @Schema(description = "PHONE이면 전화번호(예: 010-3333-3333), INSTAGRAM이면 인스타그램 아이디(예: my_insta_id)",
                example = "010-3333-3333") String contactValue,
        @NotBlank @Size(max = 100) String department,
        @NotBlank @Pattern(regexp = "^[EI][SN][TF][JP]$") String mbti,
        @NotBlank @Size(max = 500) String bio,
        @NotNull UUID photoId,
        @Size(max = 64)
        @Schema(description = """
                기존 사전신청자 재신청 초대 토큰(선택). 값이 있으면 초대받은 이메일과 email 이 같아야 하고,
                학교메일 인증을 이미 끝난 것으로 처리한다 — 별도 인증 메일을 보내지 않는다.
                일반 신청에서는 넣지 않는다.
                """) String reapplyToken
) {
}
