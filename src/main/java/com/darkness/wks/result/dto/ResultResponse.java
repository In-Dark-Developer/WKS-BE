package com.darkness.wks.result.dto;

import com.darkness.wks.result.FortuneCategory;
import com.darkness.wks.result.entity.Reading;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.Grade;
import com.darkness.wks.saju.Zodiac;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "운명 분석 결과")
public record ResultResponse(
        @Schema(description = "공유 가능한 UUID v4 결과 ID")
        UUID resultId,

        @Schema(description = "닉네임", example = "도윤")
        String nickname,

        @Schema(description = "십이간지 띠. 입춘 기준이라 양력 연도와 다를 수 있다", example = "HORSE")
        Zodiac zodiac,

        DestinyResponse destiny,

        @Schema(description = "결혼운, 자녀운, 연애운 순서")
        List<FortuneResponse> fortunes,

        @Schema(example = "파란색 팔찌")
        String luckyItem,

        @Schema(example = "야외 무대")
        String luckyPlace
) {

    public static ResultResponse from(Result result, Reading reading) {
        return new ResultResponse(
                result.getId(),
                result.getNickname(),
                Zodiac.fromYearPillar(result.getYearPillar()),
                new DestinyResponse(reading.getDestinyTitle(), reading.getDestinyContent()),
                List.of(
                        new FortuneResponse(
                                FortuneCategory.MARRIAGE,
                                Grade.of(reading.getMarriageScore()).label(),
                                reading.getMarriageContent()
                        ),
                        new FortuneResponse(
                                FortuneCategory.CHILDREN,
                                Grade.of(reading.getChildrenScore()).label(),
                                reading.getChildrenContent()
                        ),
                        new FortuneResponse(FortuneCategory.LOVE, Grade.of(reading.getLoveScore()).label(), reading.getLoveContent())
                ),
                reading.getLuckyItem(),
                reading.getLuckyPlace()
        );
    }

    @Schema(description = "운명 제목과 설명")
    public record DestinyResponse(
            @Schema(example = "깔깔깔깔깔깔깔깔깔") String title,
            @Schema(example = "당신은 특별한 운명을 타고났습니다.") String description
    ) {
    }

    @Schema(description = "항목별 운세")
    public record FortuneResponse(
            FortuneCategory category,
            @Schema(description = "SS S A+ A B+ B", example = "SS") String grade,
            @Schema(example = "결혼운의 흐름이 매우 좋습니다.") String content
    ) {
    }
}
