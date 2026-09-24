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
        @NotNull UUID photoId
) {
}
