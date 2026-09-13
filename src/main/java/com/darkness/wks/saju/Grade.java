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

    /** 0~100 점수 → 등급. 12점 간격, 분포 조정은 여기서만 */
    public static Grade of(int score) {
        if (score >= 88) return SS;
        if (score >= 76) return S;
        if (score >= 64) return A_PLUS;
        if (score >= 52) return A;
        if (score >= 40) return B_PLUS;
        return B;
    }
}
