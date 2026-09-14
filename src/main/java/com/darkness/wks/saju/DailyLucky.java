package com.darkness.wks.saju;

import com.nlf.calendar.Solar;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 오늘의 행운 오행·아이템 (기능명세서 "행운의 아이템", 2026-09-14). 매일 바뀌고 사주에 묶인다. LLM·외부 API 없음.
 * <p>
 * 오행 5개를 각각 점수화해 최고점을 고른다.
 * <ul>
 *   <li>사용자 궁합 40%: 일간 기준 관계 — 인성(나를 생) 5 &gt; 비겁 4 &gt; 식상 3 &gt; 재성 2 &gt; 관성 1. 일지 오행이면 +0.5 (보조)</li>
 *   <li>오늘 활성도 60%: 일진 천간 오행 +2, 지지 오행 +1.5, 천간·지지가 생해주면 +1씩, 극하면 −1씩</li>
 * </ul>
 * 동점: 활성도 높은 쪽 → 인성 → 비겁. 출생시간·성별은 쓰지 않는다.
 * 아이템은 {@code 사용자 식별값 + 날짜 + 오행} 해시로 골라 같은 사람·같은 날은 같고, 날짜가 바뀌면 다시 계산한다.
 * 풀은 {@code resources/lucky/items.txt} (기획 확정본).
 */
public record DailyLucky(Element element, String item) {

    private static final Map<Element, List<String>> ITEMS = LuckyPool.load("lucky/items.txt");
    /** 십성 역할 인덱스(0 비겁 1 식상 2 재성 3 관성 4 인성) → 궁합 점수 */
    private static final double[] AFFINITY = {4, 3, 2, 1, 5};
    private static final double AFFINITY_MAX = 5.5;
    private static final double ACTIVITY_MIN = -2, ACTIVITY_MAX = 3.5;

    /**
     * @param userKey 사용자 식별값 (생년월일·시간·성별). 같은 값이면 같은 날 같은 아이템
     */
    public static DailyLucky of(SajuPillars pillars, LocalDate today, String userKey) {
        String dayGanji = todayPillar(today);
        Element element = luckyElement(pillars, dayGanji);
        List<String> pool = ITEMS.get(element);
        int idx = Math.floorMod((userKey + today + element).hashCode(), pool.size());
        return new DailyLucky(element, pool.get(idx));
    }

    static Element luckyElement(SajuPillars pillars, String dayGanji) {
        Element me = Element.ofStem(pillars.dayPillar().charAt(0));
        Element spouseBranch = Element.ofBranch(pillars.dayPillar().charAt(1));
        Element todayStem = Element.ofStem(dayGanji.charAt(0));
        Element todayBranch = Element.ofBranch(dayGanji.charAt(1));

        Element best = null;
        double bestTotal = -1, bestActivity = 0;
        for (Element e : Element.values()) {
            double affinity = (AFFINITY[e.roleFor(me)] + (e == spouseBranch ? 0.5 : 0)) / AFFINITY_MAX;
            double activity = activity(e, todayStem, todayBranch);
            double total = 0.4 * affinity + 0.6 * (activity - ACTIVITY_MIN) / (ACTIVITY_MAX - ACTIVITY_MIN);
            if (best == null || total > bestTotal + 1e-9
                    || (Math.abs(total - bestTotal) < 1e-9 && tieBreak(e, best, activity, bestActivity, me))) {
                best = e;
                bestTotal = total;
                bestActivity = activity;
            }
        }
        return best;
    }

    private static double activity(Element e, Element todayStem, Element todayBranch) {
        double a = 0;
        if (e == todayStem) a += 2;
        if (e == todayBranch) a += 1.5;
        if (todayStem.generates(e)) a += 1;
        if (todayBranch.generates(e)) a += 1;
        if (todayStem.controls(e)) a -= 1;
        if (todayBranch.controls(e)) a -= 1;
        return a;
    }

    /** 동점: ① 오늘 일진에서 더 강한 오행 ② 인성 ③ 비겁 */
    private static boolean tieBreak(Element candidate, Element current, double candActivity, double curActivity, Element me) {
        if (candActivity != curActivity) return candActivity > curActivity;
        int c = candidate.roleFor(me), k = current.roleFor(me);
        if (c == 4 || k == 4) return c == 4 && k != 4;
        return c == 0 && k != 0;
    }

    /** 오늘 일진 (한글 2글자). lunar-java 의 GMT+8 기준이지만 날짜 단위라 KST 와 같다 */
    static String todayPillar(LocalDate date) {
        return SajuCalculator.toKorean(Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar().getDayInGanZhi());
    }
}
