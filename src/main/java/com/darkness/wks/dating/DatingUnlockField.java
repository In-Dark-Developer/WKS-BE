package com.darkness.wks.dating;

/** 소개팅 카드에서 해금 가능한 필드와 비용(plan.md §1.4). */
public enum DatingUnlockField {
    PHOTO(10),
    NAME(7),
    DEPARTMENT(5),
    REASON(3);

    private final int cost;

    DatingUnlockField(int cost) {
        this.cost = cost;
    }

    public int cost() {
        return cost;
    }
}
