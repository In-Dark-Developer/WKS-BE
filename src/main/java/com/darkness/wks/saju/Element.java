package com.darkness.wks.saju;

import java.util.List;

/**
 * 오행. 순서가 상생 순환(목→화→토→금→수→목)이라 ordinal 차이로 관계를 구한다.
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

    /** 년·월·일·시 기둥 비중과 지지 비중. ReadingScorer 와 같은 값 */
    private static final double[] PILLAR_WEIGHT = {0.7, 1.3, 1.0, 0.9};
    private static final double BRANCH_WEIGHT = 0.85;

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

    /** 내가 낳는 오행 (상생) */
    public boolean generates(Element other) {
        return (ordinal() + 1) % 5 == other.ordinal();
    }

    /** 내가 이기는 오행 (상극) */
    public boolean controls(Element other) {
        return (ordinal() + 2) % 5 == other.ordinal();
    }

    /**
     * 일간(me) 기준 십성 역할: 0 비겁(같음), 1 식상(내가 생), 2 재성(내가 극), 3 관성(나를 극), 4 인성(나를 생).
     * 상생 순환에서 me 로부터 몇 칸 뒤인지.
     */
    public int roleFor(Element me) {
        return (ordinal() - me.ordinal() + 5) % 5;
    }

    /**
     * 원국 오행 세력. 기둥 비중(년 0.7·월 1.3·일 1.0·시 0.9)과 지지 0.85 로 8글자(시주 없으면 6글자)를 더한다.
     * 지장간·통근은 반영하지 않는다.
     */
    public static double[] strengths(SajuPillars p) {
        List<String> all = p.hourPillar() == null
                ? List.of(p.yearPillar(), p.monthPillar(), p.dayPillar())
                : List.of(p.yearPillar(), p.monthPillar(), p.dayPillar(), p.hourPillar());
        double[] s = new double[5];
        for (int i = 0; i < all.size(); i++) {
            s[ofStem(all.get(i).charAt(0)).ordinal()] += PILLAR_WEIGHT[i];
            s[ofBranch(all.get(i).charAt(1)).ordinal()] += PILLAR_WEIGHT[i] * BRANCH_WEIGHT;
        }
        return s;
    }
}
