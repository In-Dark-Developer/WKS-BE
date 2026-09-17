package com.darkness.wks.result.dto;

import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.result.FortuneCategory;
import com.darkness.wks.result.entity.Reading;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.DailyLucky;
import com.darkness.wks.saju.DestinyTitle;
import com.darkness.wks.saju.Element;
import com.darkness.wks.saju.LuckyPlace;
import com.darkness.wks.saju.Grade;
import com.darkness.wks.saju.SajuPillars;
import com.darkness.wks.saju.Zodiac;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

        @Schema(description = "사주 원국의 오행 개수. 출생 시간 입력 시 합계 8, 미입력 시 합계 6")
        ElementResponse elements,

        @Schema(description = "오늘의 행운 아이템. 팔자 + 오늘 일진으로 계산, 매일 바뀜", example = "파란 부채")
        String luckyItem,

        @Schema(description = "행운의 장소(동국대 안). 원국 기준이라 사람마다 고정", example = "팔정도")
        String luckyPlace,

        @Schema(description = "생성 시 빈 배열, 조회 시 createdAt 내림차순")
        List<CompatibilityResponse> compatibilities
) {

    public static ResultResponse from(Result result, Reading reading) {
        return from(result, reading, List.of());
    }

    public static ResultResponse from(Result result, Reading reading, List<Compatibility> compatibilities) {
        SajuPillars pillars = new SajuPillars(result.getYearPillar(), result.getMonthPillar(), result.getDayPillar(), result.getHourPillar());
        // 키는 입력값. 같은 생년월일·시간·성별이면 같은 날 같은 아이템 (다시 생성해도 동일). 날짜가 바뀌면 재계산
        String luckyKey = result.getBirthDate() + "/" + result.getBirthTime() + "/" + result.getGender();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        DailyLucky lucky = DailyLucky.of(pillars, today, luckyKey);
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
                ElementResponse.from(pillars),
                lucky.item(),
                LuckyPlace.of(pillars, today),
                compatibilities.stream()
                        .map(compatibility -> CompatibilityResponse.from(compatibility, result))
                        .toList()
        );
    }

    @Schema(description = "사주 원국 6~8글자의 오행 분포")
    public record ElementResponse(
            @Schema(description = "목 개수", example = "3") int wood,
            @Schema(description = "화 개수", example = "2") int fire,
            @Schema(description = "토 개수", example = "1") int earth,
            @Schema(description = "금 개수", example = "1") int metal,
            @Schema(description = "수 개수", example = "1") int water
    ) {
        public static ElementResponse from(SajuPillars pillars) {
            Map<Element, Integer> counts = new EnumMap<>(Element.class);
            for (Element element : Element.values()) {
                counts.put(element, 0);
            }

            count(counts, pillars.yearPillar());
            count(counts, pillars.monthPillar());
            count(counts, pillars.dayPillar());
            if (pillars.hourPillar() != null) {
                count(counts, pillars.hourPillar());
            }

            return new ElementResponse(
                    counts.get(Element.WOOD),
                    counts.get(Element.FIRE),
                    counts.get(Element.EARTH),
                    counts.get(Element.METAL),
                    counts.get(Element.WATER)
            );
        }

        private static void count(Map<Element, Integer> counts, String pillar) {
            counts.merge(Element.ofStem(pillar.charAt(0)), 1, Integer::sum);
            counts.merge(Element.ofBranch(pillar.charAt(1)), 1, Integer::sum);
        }
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
