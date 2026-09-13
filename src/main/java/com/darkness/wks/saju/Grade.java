package com.darkness.wks.saju;

/** 운세 등급 6단계 (2026-09-13 기획 확정). 프론트에는 {@link #label()} 이 나간다. */
public enum Grade {
    SS("SS"),
    S("S"),
    A_PLUS("A+"),
    A("A"),
    B_PLUS("B+"),
    B("B");

    private final String label;

    Grade(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 0~100 점수 → 등급. 컷은 기획 확정값 (2026-09-13): SS 94 / S 84 / A+ 74 / A 64 / B+ 52 */
    public static Grade of(int score) {
        if (score >= 94) return SS;
        if (score >= 84) return S;
        if (score >= 74) return A_PLUS;
        if (score >= 64) return A;
        if (score >= 52) return B_PLUS;
        return B;
    }
}
