package com.darkness.wks.wallet;

/**
 * plan.md §9.4 의 reason 목록 그대로. {@code PARTNER} 는 제휴처 유입 보상(2026-09-28, ref_id = 제휴 코드,
 * {@link PartnerRewardService}). {@code REQUEST} 는 매칭 요청이 무료로 결정돼(2026-09-24) 값만 예약해 두고 쓰지 않는다.
 * {@code REROLL} 은 2026-09-27 TBD-6 종료로 사용한다 — 무료 리롤도 횟수를 세려고 {@code amount = 0}
 * 으로 남긴다.
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
