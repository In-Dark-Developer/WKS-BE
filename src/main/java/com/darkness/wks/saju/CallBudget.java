package com.darkness.wks.saju;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 최근 60초·태평양 날짜별 호출 제한. 실패한 외부 호출도 예산을 소비한다.
 * ponytail: 프로세스 메모리 예산. 여러 인스턴스가 프로젝트를 공유하면 공용 저장소로 옮긴다.
 */
final class CallBudget {
    static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private final int perMinute;
    private final int perDay;
    private final Clock clock;
    private final Deque<Instant> calls = new ArrayDeque<>();
    private LocalDate dayKey;
    private int dayCount;

    CallBudget(int perMinute, int perDay, Clock clock) {
        this.perMinute = perMinute;
        this.perDay = perDay;
        this.clock = clock;
    }

    synchronized boolean available() {
        Instant now = clock.instant();
        while (!calls.isEmpty() && !calls.peekFirst().isAfter(now.minusSeconds(60))) {
            calls.removeFirst();
        }
        LocalDate day = now.atZone(PACIFIC).toLocalDate();
        if (!day.equals(dayKey)) {
            dayKey = day;
            dayCount = 0;
        }
        return calls.size() < perMinute && dayCount < perDay;
    }

    synchronized boolean tryAcquire() {
        if (!available()) return false;
        calls.addLast(clock.instant());
        dayCount++;
        return true;
    }

    synchronized String status() {
        available();
        return "minute=" + calls.size() + "/" + perMinute + " day=" + dayCount + "/" + perDay;
    }
}
