package com.darkness.wks.saju;

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
     * 오늘 천간 오행과 나(일간)의 관계(십성)에 대응하는 행운 오행.
     * 비겁(같음)→식상, 식상→재성, 재성→관성, 관성→인성, 인성→비겁. 즉 관계 순환에서 한 칸 뒤.
     */
    public Element luckyAgainst(Element today) {
        int relation = (today.ordinal() - ordinal() + 5) % 5; // 0 비겁 1 식상 2 재성 3 관성 4 인성
        return values()[(ordinal() + relation + 1) % 5];
    }
}
