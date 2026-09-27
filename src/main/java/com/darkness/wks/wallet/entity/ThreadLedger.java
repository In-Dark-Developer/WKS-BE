package com.darkness.wks.wallet.entity;

import com.darkness.wks.wallet.LedgerReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 실(재화) 원장의 행 하나. 증감만 기록하고 잔액은 여기의 합계로 계산한다(잔액 컬럼 없음) —
 * plan.md §9.4. 한 번 쓰인 행은 고치거나 지우지 않는다(감사 추적).
 */
@Entity
@Table(name = "thread_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThreadLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    /** 양수면 지급, 음수면 차감. */
    @Column(name = "amount", nullable = false, updatable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 20)
    private LedgerReason reason;

    /** {@code UNIQUE(member_id, reason, ref_id)} 로 중복 지급·차감을 막는 키. 절대 NULL 이면 안 된다. */
    @Column(name = "ref_id", nullable = false, updatable = false, length = 100)
    private String refId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ThreadLedger(Long memberId, int amount, LedgerReason reason, String refId) {
        this.memberId = memberId;
        this.amount = amount;
        this.reason = reason;
        this.refId = refId;
    }
}
