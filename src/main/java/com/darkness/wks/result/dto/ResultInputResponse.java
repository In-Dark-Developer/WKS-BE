package com.darkness.wks.result.dto;

import com.darkness.wks.common.Gender;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.CalendarType;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * 입력 폼 자동 채움용 응답 (#66). 필드 구성은 {@link CreateResultRequest} 와 같아서 그대로 폼에 넣으면 된다.
 * 음력으로 입력했으면 음력 날짜가 그대로 나온다.
 */
@Schema(description = "결과를 만들 때 입력한 값. 폼 자동 채움용")
public record ResultInputResponse(
        @Schema(description = "닉네임", example = "도윤") String nickname,
        @Schema(description = "달력 종류", example = "SOLAR") CalendarType calendarType,
        @Schema(description = "입력한 생년월일(yyyy-MM-dd). LUNAR 면 음력 날짜", example = "2002-03-14") String birthDate,
        @Schema(description = "음력 윤달 여부", example = "false") boolean isLeapMonth,
        @Schema(description = "출생 시각(HH:mm), 모르면 null", example = "14:30", nullable = true)
        @JsonFormat(pattern = "HH:mm") LocalTime birthTime,
        @Schema(description = "성별", example = "MALE") Gender gender
) {

    public static ResultInputResponse from(Result result) {
        return new ResultInputResponse(
                result.getNickname(),
                result.getCalendarType(),
                result.getBirthDateInput(),
                result.isLeapMonth(),
                result.getBirthTime(),
                result.getGender()
        );
    }
}
