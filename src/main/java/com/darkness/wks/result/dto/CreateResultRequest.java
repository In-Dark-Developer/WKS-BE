package com.darkness.wks.result.dto;

import com.darkness.wks.common.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "사주 결과 생성 요청")
public record CreateResultRequest(
        @Schema(description = "닉네임", example = "도윤")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 8, message = "닉네임은 8자 이하여야 합니다.")
        String nickname,

        @Schema(description = "생년월일(yyyy-MM-dd)", example = "2002-03-14")
        @NotNull(message = "생년월일은 필수입니다.")
        @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다.")
        LocalDate birthDate,

        @Schema(description = "출생 시각(HH:mm), 모르면 null", example = "14:30", nullable = true)
        LocalTime birthTime,

        @Schema(description = "출생 지역, 모르면 null", example = "서울", maxLength = 50, nullable = true)
        @Size(max = 50, message = "출생 지역은 50자 이하여야 합니다.")
        String birthRegion,

        @Schema(description = "성별", example = "MALE")
        @NotNull(message = "성별은 필수입니다.")
        Gender gender
) {

    @AssertTrue(message = "생년월일은 1950-01-01 이후여야 합니다.")
    @Schema(hidden = true)
    public boolean isBirthDateInRange() {
        return birthDate == null || !birthDate.isBefore(LocalDate.of(1950, 1, 1));
    }
}
