package com.darkness.wks.wallet;

/**
 * plan.md §9.4 의 reason 목록 그대로. {@code PARTNER}·{@code REQUEST}·{@code REROLL} 은 각각 제휴처
 * 지급 방식·매칭 요청(2026-09-24 무료로 결정, 원장에 안 남음)·리롤(TBD-6, 비용 미정)이 정해지기 전까지
 * 값만 예약해 두고 어디서도 쓰지 않는다.
 */
public enum LedgerReason {
    SIGNUP_BONUS,
    CHECK_IN,
    MAP_FRIEND,
    PARTNER,
    UNLOCK,
    REQUEST,
    REROLL
}
