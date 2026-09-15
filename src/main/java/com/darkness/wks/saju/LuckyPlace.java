package com.darkness.wks.saju;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 행운의 장소 (기능명세서 "행운의 장소", 2026-09-14). 오행은 원국 기준이라 사람마다 고정, 장소는 그 오행 풀 안에서 매일 바뀐다 (2026-09-15 결정).
 * <p>
 * 원국 오행 세력({@link Element#strengths})으로 일간 강약을 보고, 균형을 가장 잘 보완하는 오행을 고른다.
 * <ul>
 *   <li>신약(나를 돕는 세력 &lt; 40%): 인성·비겁 중에서</li>
 *   <li>신강(&gt; 55%): 식상·재성·관성 중에서</li>
 *   <li>균형: 다섯 오행 모두</li>
 * </ul>
 * 후보 중 현재 세력이 가장 적은 오행 = 보완 오행. 동점은 목→화→토→금→수 순.
 * ponytail: 명세의 지장간·통근·월령 보정은 생략. 시주 없으면 3주로만 계산.
 * 장소는 {@code resources/lucky/places.txt} 풀에서 {@code 팔자 + 날짜} 해시로 하나. 풀이 3~4곳이라 며칠에 한 번은 같은 곳이 나온다.
 */
public final class LuckyPlace {

    private static final Map<Element, List<String>> PLACES = LuckyPool.load("lucky/places.txt");

    private LuckyPlace() {
    }

    public static String of(SajuPillars pillars, LocalDate today) {
        Element element = luckyElement(pillars);
        return LuckyPool.pick(PLACES.get(element), pillars.toString() + today);
    }

    static Element luckyElement(SajuPillars pillars) {
        Element me = Element.ofStem(pillars.dayPillar().charAt(0));
        double[] strength = Element.strengths(pillars);
        double total = 0, support = 0;
        for (Element e : Element.values()) {
            total += strength[e.ordinal()];
            int role = e.roleFor(me);
            if (role == 0 || role == 4) support += strength[e.ordinal()]; // 비겁·인성 = 나를 돕는 세력
        }
        double ratio = support / total;

        Element best = null;
        for (Element e : Element.values()) {
            int role = e.roleFor(me);
            boolean helps = role == 0 || role == 4;
            if (ratio < 0.40 && !helps) continue;   // 신약: 돕는 오행만
            if (ratio > 0.55 && helps) continue;    // 신강: 빼는 오행만
            if (best == null || strength[e.ordinal()] < strength[best.ordinal()]) best = e;
        }
        return best;
    }
}
