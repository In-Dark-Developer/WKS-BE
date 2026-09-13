package com.darkness.wks.result.dto;

import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.result.FortuneCategory;
import com.darkness.wks.result.entity.Reading;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.DailyLucky;
import com.darkness.wks.saju.DestinyTitle;
import com.darkness.wks.saju.Grade;
import com.darkness.wks.saju.SajuPillars;
import com.darkness.wks.saju.Zodiac;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Schema(description = "운명 분석 결과")
public record ResultResponse(
        @Schema(description = "본인 결과 조회용 UUID v4 ID")
        UUID resultId,

        @Schema(description = "친구에게 전달하는 공개 링크 UUID v4 ID")
        UUID shareId,

        @Schema(description = "닉네임", example = "도윤")
        String nickname,

        @Schema(description = "십이간지 띠. 입춘 기준이라 양력 연도와 다를 수 있다", example = "HORSE")
        Zodiac zodiac,

        DestinyResponse destiny,

        @Schema(description = "결혼운, 자녀운, 연애운 순서")
        List<FortuneResponse> fortunes,

        @Schema(description = "오늘의 행운 아이템. 팔자 + 오늘 일진으로 계산, 매일 바뀜", example = "파란 부채")
        String luckyItem,

        @Schema(description = "오늘의 행운 장소(동국대 안). 매일 바뀜", example = "팔정도 앞")
        String luckyPlace,

        @Schema(description = "생성 시 빈 배열, 조회 시 createdAt 내림차순")
        List<CompatibilityResponse> compatibilities
) {

    public static ResultResponse from(Result result, Reading reading) {
        return from(result, reading, List.of());
    }

    public static ResultResponse from(Result result, Reading reading, List<Compatibility> compatibilities) {
        DailyLucky lucky = DailyLucky.of(
                new SajuPillars(result.getYearPillar(), result.getMonthPillar(), result.getDayPillar(), result.getHourPillar()),
                LocalDate.now(ZoneId.of("Asia/Seoul")));
        return new ResultResponse(
                result.getId(),
                result.getShareId(),
                result.getNickname(),
                Zodiac.fromYearPillar(result.getYearPillar()),
                new DestinyResponse(
                        DestinyTitle.of(reading.getMarriageScore(), reading.getChildrenScore(), reading.getLoveScore()),
                        reading.getDestinyContent()),
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
                lucky.item(),
                lucky.place(),
                compatibilities.stream()
                        .map(compatibility -> CompatibilityResponse.from(compatibility, result))
                        .toList()
        );
    }

    @Schema(description = "운명 제목과 설명")
    public record DestinyResponse(
            @Schema(description = "결혼·자녀·연애 상/하 조합 8종 중 하나", example = "사랑이 앞서 걷는 길") String title,
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

    @Schema(description = "친구 궁합 요약. 상대방 개인정보와 resultId는 포함하지 않는다.")
    public record CompatibilityResponse(
            @Schema(example = "지현") String nickname,
            @Schema(minimum = "0", maximum = "100", example = "82") int score,
            CompatibilityTier tier,
            Instant createdAt
    ) {

        public static CompatibilityResponse from(Compatibility compatibility, Result result) {
            Result other = compatibility.getOrigin().getId().equals(result.getId())
                    ? compatibility.getGuest()
                    : compatibility.getOrigin();
            return new CompatibilityResponse(
                    other.getNickname(),
                    compatibility.getScore(),
                    compatibility.getTier(),
                    compatibility.getCreatedAt()
            );
        }
    }
}
