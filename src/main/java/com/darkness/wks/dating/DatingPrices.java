package com.darkness.wks.dating;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 소개팅 실 가격표. 리롤·해금 비용을 응답(표시)과 차감이 같은 값을 쓰도록 한 곳에 모은다.
 *
 * <p>축제 마지막 날 할인(2026-10-01 결정, 실험): 10/1 10:00 KST 부터 리롤 20→10, 사진 10→5, 이름 7→3,
 * 학과 5→2, 궁합 이유 3→1. 끝나는 시각은 없다 — 축제가 그날 끝나서 되돌릴 일이 없다. 배포 시각과 무관하게
 * 정해진 시각에 바뀌도록 시각으로 판정한다.
 */
final class DatingPrices {

    static final Instant FINAL_DAY_SALE_START =
            OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9)).toInstant();

    private static final int REROLL = 20;
    private static final int REROLL_ON_SALE = 10;

    // 테스트가 할인 전후를 고정하려고 바꾼다. 운영 코드에서는 건드리지 않는다.
    static Clock clock = Clock.systemUTC();

    private DatingPrices() {
    }

    static boolean onSale() {
        return !clock.instant().isBefore(FINAL_DAY_SALE_START);
    }

    static int reroll() {
        return onSale() ? REROLL_ON_SALE : REROLL;
    }

    static int unlock(DatingUnlockField field) {
        return onSale() ? field.saleCost() : field.regularCost();
    }
}
