package com.darkness.wks.result.dto;

import com.darkness.wks.saju.Zodiac;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "공유 링크에서 보여주는 공개 사주 결과")
public record SharedResultResponse(
        @Schema(example = "서연") String nickname,
        Zodiac zodiac,
        ResultResponse.DestinyResponse destiny,
        List<ResultResponse.FortuneResponse> fortunes,
        String luckyItem,
        String luckyPlace,
        List<ResultResponse.CompatibilityResponse> compatibilities
) {

    public static SharedResultResponse from(ResultResponse result) {
        return new SharedResultResponse(
                result.nickname(),
                result.zodiac(),
                result.destiny(),
                result.fortunes(),
                result.luckyItem(),
                result.luckyPlace(),
                result.compatibilities()
        );
    }
}
