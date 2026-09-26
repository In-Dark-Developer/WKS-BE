package com.darkness.wks.dating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** 소개팅 학교메일 6자리 코드. 회원당 최근 발송분 한 행만 둔다 (V24) */
@Entity
@Table(name = "dating_email_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatingEmailCode {

    @Id
    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "send_window_start", nullable = false)
    private Instant sendWindowStart;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DatingEmailCode(Long memberId, String email, String codeHash, Instant expiresAt, Instant now) {
        this.memberId = memberId;
        this.sendWindowStart = now;
        this.sendCount = 0;
        reissue(email, codeHash, expiresAt, now, false);
    }

    /** 새 코드로 덮어쓴다. 이전 코드·인증 상태·실패 횟수는 여기서 무효가 된다 */
    public void reissue(String email, String codeHash, Instant expiresAt, Instant now, boolean resetWindow) {
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.failedAttempts = 0;
        this.verifiedAt = null;
        this.lastSentAt = now;
        if (resetWindow) {
            this.sendWindowStart = now;
            this.sendCount = 0;
        }
        this.sendCount++;
    }

    public void recordFailure() {
        failedAttempts++;
    }

    public void markVerified(Instant time) {
        verifiedAt = time;
    }

    public boolean isVerifiedFor(String email) {
        return verifiedAt != null && this.email.equals(email);
    }
}
