package com.darkness.wks.saju;

/** 응답 순서 고정: MARRIAGE → CHILDREN → LOVE (api-spec §2) */
public enum ReadingCategory {
    MARRIAGE("결혼운"),
    CHILDREN("자녀운"),
    LOVE("연애운");

    private final String korean;

    ReadingCategory(String korean) {
        this.korean = korean;
    }

    public String korean() {
        return korean;
    }
}
