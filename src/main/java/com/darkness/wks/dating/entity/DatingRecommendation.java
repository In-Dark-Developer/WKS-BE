package com.darkness.wks.dating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "dating_recommendation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatingRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "viewer_member_id", nullable = false, updatable = false)
    private Long viewerMemberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_profile_id", nullable = false, updatable = false)
    private DatingProfile candidate;

    @Column(name = "score", nullable = false, updatable = false)
    private int score;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DatingRecommendation(Long viewerMemberId, DatingProfile candidate, int score) {
        this.viewerMemberId = viewerMemberId;
        this.candidate = candidate;
        this.score = score;
        this.active = true;
    }

    public void deactivate() {
        active = false;
    }
}
