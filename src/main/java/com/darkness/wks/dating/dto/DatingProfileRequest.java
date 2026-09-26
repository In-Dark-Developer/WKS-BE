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
        // 2026-09-27 부터 쓰지 않는다. 재신청 초대가 학교메일 인증을 대신하지 않게 바뀌어서 서버가 무시한다.
        // 프론트 계약의 필드 삭제라 팀 확인 전까지는 받기만 한다(AGENTS.md)
        @Size(max = 64)
        @Schema(deprecated = true, description = """
                폐기 예정 — 서버가 무시한다. 재신청 사전신청자도 학교메일 코드 인증(POST /api/dating/email-codes)을 거친다.
                """) String reapplyToken
) {
}
