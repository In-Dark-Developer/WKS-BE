package com.darkness.wks.result.dto;

import com.darkness.wks.common.Gender;
import com.darkness.wks.saju.CalendarType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

@Schema(description = "사주 결과 생성 요청")
public record CreateResultRequest(
        @Schema(description = "닉네임", example = "도윤")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 8, message = "닉네임은 8자 이하여야 합니다.")
        String nickname,

        @Schema(description = "달력 종류", example = "SOLAR")
        @NotNull(message = "달력 종류는 필수입니다.")
        CalendarType calendarType,

        // 음력 2월 30일처럼 LocalDate 로 표현 못 하는 날짜가 있어 문자열로 받는다. 존재 여부·범위는 서비스에서 검증
        @Schema(description = "생년월일(yyyy-MM-dd). LUNAR 면 음력 날짜", example = "2002-03-14")
        @NotBlank(message = "생년월일은 필수입니다.")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "생년월일은 yyyy-MM-dd 형식이어야 합니다.")
        String birthDate,

        @Schema(description = "음력 윤달 여부. LUNAR 일 때만 의미, 생략 시 false", example = "false", nullable = true)
        Boolean isLeapMonth,

        @Schema(description = "출생 시각(HH:mm), 모르면 null. 시진 선택 UI 는 칸의 가운데 시각을 보낸다", example = "14:30", nullable = true)
        LocalTime birthTime,

        @Schema(description = "성별", example = "MALE")
        @NotNull(message = "성별은 필수입니다.")
        Gender gender
) {

    public boolean leapMonth() {
        return Boolean.TRUE.equals(isLeapMonth);
    }
}
