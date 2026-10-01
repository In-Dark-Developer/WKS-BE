package com.darkness.wks.dating;

/** 소개팅 카드에서 해금 가능한 필드와 비용(plan.md §1.4). 마지막 날 할인가는 {@link DatingPrices}. */
public enum DatingUnlockField {
    PHOTO(10, 5),
    NAME(7, 3),
    DEPARTMENT(5, 2),
    REASON(3, 1);

    private final int regularCost;
    private final int saleCost;

    DatingUnlockField(int regularCost, int saleCost) {
        this.regularCost = regularCost;
        this.saleCost = saleCost;
    }

    /** 지금 해금하면 드는 실. 응답 표시와 차감이 모두 이 값을 쓴다. */
    public int cost() {
        return DatingPrices.unlock(this);
    }

    int regularCost() {
        return regularCost;
    }

    int saleCost() {
        return saleCost;
    }
}
