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
 * 기존 사전신청자 재신청 초대 토큰. {@link EmailVerification} 과 달리 클릭 시점에 소비되지 않는다 —
 * 카카오 로그인·사진 업로드를 거쳐 소개팅 프로필 생성이 끝날 때 소비한다.
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

    public void markUsed(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
