package com.darkness.wks.compatibility.entity;

public enum CompatibilityTier {
    GUIIN("귀인"),
    CHALTTEOK("찰떡"),
    BEOT("벗"),
    SEUCHIM("스침");

    private final String korean;

    CompatibilityTier(String korean) {
        this.korean = korean;
    }

    /** 화면·LLM 프롬프트에 쓰는 한글 이름 */
    public String korean() {
        return korean;
    }

    public static CompatibilityTier fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Compatibility score must be between 0 and 100");
        }
        if (score >= 90) {
            return GUIIN;
        }
        if (score >= 75) {
            return CHALTTEOK;
        }
        if (score >= 61) {
            return BEOT;
        }
        return SEUCHIM;
    }
}
