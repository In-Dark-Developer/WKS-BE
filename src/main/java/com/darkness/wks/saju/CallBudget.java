package com.darkness.wks.saju;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Gemini 호출 총량 상한 (#64). 분당·일일 카운터. 무료 한도(프로젝트 단위)를 스크립트 도배가 태워
 * 실사용자까지 막히는 것을 우리 쪽에서 먼저 끊는다. IP 제한은 축제장 NAT 때문에 좁게 못 잡아 총량으로 지킨다.
 * <p>
 * 일일 창은 Google 과 같은 태평양 자정 기준(= KST 16:00 또는 17:00). 재시도도 한도를 쓰므로 호출 시도마다 센다.
 * ponytail: 서버 1대라 메모리 카운터. 재시작하면 일일 카운터가 0 부터 → 실제보다 관대. 축제 규모에선 무시
 */
final class CallBudget {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    private final int perMinute;
    private final int perDay;
    private final Clock clock;
    private long minuteKey = -1;
    private int minuteCount;
    private LocalDate dayKey;
    private int dayCount;

    CallBudget(int perMinute, int perDay, Clock clock) {
        this.perMinute = perMinute;
        this.perDay = perDay;
        this.clock = clock;
    }

    /** 호출 허용이면 카운트하고 true. 상한이면 false (카운트 안 함) */
    synchronized boolean tryAcquire() {
        long minute = clock.millis() / 60_000;
        if (minute != minuteKey) {
            minuteKey = minute;
            minuteCount = 0;
        }
        LocalDate day = LocalDate.now(clock.withZone(PACIFIC));
        if (!day.equals(dayKey)) {
            dayKey = day;
            dayCount = 0;
        }
        if (minuteCount >= perMinute || dayCount >= perDay) return false;
        minuteCount++;
        dayCount++;
        return true;
    }

    synchronized String status() {
        return "minute=" + minuteCount + "/" + perMinute + " day=" + dayCount + "/" + perDay;
    }
}
