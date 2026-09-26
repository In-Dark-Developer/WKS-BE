package com.darkness.wks.signup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 기존 사전신청자 재신청 초대 토큰. {@link EmailVerification} 과 달리 소비되지 않고 만료로만 끝난다 —
 * 완료 여부는 사주 결과가 계정에 연결됐는지로 판단한다({@code SignupRepository.findReapplyTargets}).
 * {@code used_at} 은 2026-09-27 이전(초대가 학교메일 인증을 대신하던 때)에만 기록됐다.
 */
@Entity
@Table(name = "signup_reapply_invite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SignupReapplyInvite {

    @Id
    @Column(name = "token", length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signup_id", nullable = false)
    private Signup signup;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SignupReapplyInvite(String token, Signup signup, Instant expiresAt) {
        this.token = token;
        this.signup = signup;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
