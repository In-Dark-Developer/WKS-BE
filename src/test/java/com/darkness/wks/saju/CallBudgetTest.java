package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CallBudgetTest {

    /** 조절 가능한 시계 */
    private static final class TestClock extends Clock {
        Instant now;
        TestClock(Instant now) { this.now = now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    @Test
    void minuteCapThenRollsOverNextMinute() {
        TestClock clock = new TestClock(Instant.parse("2026-09-20T03:00:00Z"));
        CallBudget b = new CallBudget(2, 100, clock);
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isFalse();
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThat(b.tryAcquire()).isTrue();
    }

    @Test
    void dayCapResetsAtPacificMidnight() {
        // 태평양 자정 = 2026-09-20 07:00Z (PDT). 그 1분 전엔 같은 날, 이후엔 새 날
        TestClock clock = new TestClock(Instant.parse("2026-09-20T06:59:00Z"));
        CallBudget b = new CallBudget(100, 1, clock);
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isFalse();
        clock.now = Instant.parse("2026-09-20T06:59:30Z");
        assertThat(b.tryAcquire()).isFalse(); // 아직 같은 날
        clock.now = Instant.parse("2026-09-20T07:00:00Z");
        assertThat(b.tryAcquire()).isTrue();  // 리셋
    }

    @Test
    void rejectedCallsAreNotCounted() {
        TestClock clock = new TestClock(Instant.parse("2026-09-20T03:00:00Z"));
        CallBudget b = new CallBudget(1, 100, clock);
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isFalse();
        assertThat(b.status()).isEqualTo("minute=1/1 day=1/100");
    }
}
