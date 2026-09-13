package com.darkness.wks.saju;

import java.util.List;

/**
 * 오행. 순서가 상생 순환(목→화→토→금→수→목)이라 ordinal 차이로 십성 관계를 구한다.
 */
public enum Element {
    WOOD("목", "청색·녹색"),
    FIRE("화", "적색·분홍"),
    EARTH("토", "황색·갈색"),
    METAL("금", "백색·은색"),
    WATER("수", "흑색·남색");

    private static final String STEMS = "갑을병정무기경신임계";
    private static final String BRANCHES = "자축인묘진사오미신유술해";
    private static final Element[] STEM_ELEMENT = {WOOD, WOOD, FIRE, FIRE, EARTH, EARTH, METAL, METAL, WATER, WATER};
    private static final Element[] BRANCH_ELEMENT = {WATER, EARTH, WOOD, WOOD, EARTH, FIRE, FIRE, EARTH, METAL, METAL, EARTH, WATER};

    private final String korean;
    private final String colors;

    Element(String korean, String colors) {
        this.korean = korean;
        this.colors = colors;
    }

    public String korean() {
        return korean;
    }

    public String colors() {
        return colors;
    }

    public static Element ofStem(char stem) {
        return STEM_ELEMENT[STEMS.indexOf(stem)];
    }

    public static Element ofBranch(char branch) {
        return BRANCH_ELEMENT[BRANCHES.indexOf(branch)];
    }

    /**
     * 팔자에서 가장 적게 나온 오행 = 용신 근사. 행운 아이템·장소의 기준.
     * 동률이면 목→화→토→금→수 순서에서 앞선 것. 결정적.
     */
    public static Element lacking(SajuPillars p) {
        int[] count = new int[values().length];
        for (String pillar : List.of(p.yearPillar(), p.monthPillar(), p.dayPillar(), p.hourPillar() == null ? "" : p.hourPillar())) {
            if (pillar.isEmpty()) continue;
            count[ofStem(pillar.charAt(0)).ordinal()]++;
            count[ofBranch(pillar.charAt(1)).ordinal()]++;
        }
        Element min = WOOD;
        for (Element e : values()) {
            if (count[e.ordinal()] < count[min.ordinal()]) min = e;
        }
        return min;
    }
}
