package com.darkness.wks.compatibility.entity;

public enum CompatibilityTier {
    GUIIN,
    CHALTTEOK,
    BEOT,
    SEUCHIM;

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
