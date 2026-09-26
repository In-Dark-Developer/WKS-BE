package com.darkness.wks.dating.entity;

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

@Entity
@Table(name = "dating_email_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatingEmailVerification {

    @Id
    @Column(name = "token", length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dating_profile_id", nullable = false)
    private DatingProfile profile;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DatingEmailVerification(String token, DatingProfile profile, Instant expiresAt) {
        this.token = token;
        this.profile = profile;
        this.expiresAt = expiresAt;
    }

    public void markUsed(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
