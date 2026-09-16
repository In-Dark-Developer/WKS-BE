package com.darkness.wks.saju;

import com.darkness.wks.common.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 원국의 오행 요약. 화면 표시와 해석 프롬프트가 같은 값을 쓴다 (#55).
 * 배우자·자녀 기운은 성별 기준: 남자 재성·관성, 여자 관성·식상 ({@link ReadingScorer} 와 동일).
 * 세력은 {@link Element#strengths} 를 정수 %로 (합 100). 시주 없으면 3주 기준.
 *
 * @param mine     나의 기운 (일간)
 * @param spouse   배우자 인연을 뜻하는 기운
 * @param children 자녀 인연을 뜻하는 기운
 * @param strength 오행별 세력 %. 다섯 값의 합은 100
 */
@Schema(description = "원국 오행 요약. 배우자·자녀 기운은 성별 기준. strength 는 오행별 세력 % (합 100)")
public record ElementProfile(
        @Schema(description = "나의 기운(일간 오행)", example = "EARTH") Element mine,
        @Schema(description = "배우자 인연을 뜻하는 기운. 남자 재성, 여자 관성", example = "WATER") Element spouse,
        @Schema(description = "자녀 인연을 뜻하는 기운. 남자 관성, 여자 식상", example = "WOOD") Element children,
        @Schema(description = "오행별 세력 %. 합 100", example = "{\"WOOD\":10,\"FIRE\":35,\"EARTH\":20,\"METAL\":5,\"WATER\":30}")
        Map<Element, Integer> strength
) {

    public static ElementProfile of(SajuPillars pillars, Gender gender) {
        Element me = Element.ofStem(pillars.dayPillar().charAt(0));
        int spouseRole = gender == Gender.MALE ? 2 : 3; // 남 재성, 여 관성
        int childRole = gender == Gender.MALE ? 3 : 1;  // 남 관성, 여 식상
        Element spouse = null, child = null;
        for (Element e : Element.values()) {
            if (e.roleFor(me) == spouseRole) spouse = e;
            if (e.roleFor(me) == childRole) child = e;
        }
        return new ElementProfile(me, spouse, child, percent(Element.strengths(pillars)));
    }

    /** 세력 상위 2개 (프롬프트의 "강한 기운") */
    public List<Element> strong() {
        return byStrength().subList(0, 2);
    }

    /** 세력 10% 미만 (프롬프트의 "약한 기운"). 없을 수 있다 */
    public List<Element> weak() {
        return byStrength().stream().filter(e -> strength.get(e) < 10).toList();
    }

    private List<Element> byStrength() {
        return Arrays.stream(Element.values())
                .sorted(Comparator.comparingInt((Element e) -> -strength.get(e)).thenComparing(Element::ordinal))
                .toList();
    }

    /** 실수 세력 → 정수 %, 합이 정확히 100 이 되게 최대 나머지 순으로 보정 */
    private static Map<Element, Integer> percent(double[] raw) {
        double total = 0;
        for (double v : raw) total += v;
        int[] pct = new int[5];
        double[] rem = new double[5];
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            double exact = raw[i] / total * 100;
            pct[i] = (int) Math.floor(exact);
            rem[i] = exact - pct[i];
            sum += pct[i];
        }
        for (int left = 100 - sum; left > 0; left--) {
            int best = 0;
            for (int i = 1; i < 5; i++) if (rem[i] > rem[best]) best = i;
            pct[best]++;
            rem[best] = -1;
        }
        Map<Element, Integer> map = new EnumMap<>(Element.class);
        for (Element e : Element.values()) map.put(e, pct[e.ordinal()]);
        return map;
    }
}
