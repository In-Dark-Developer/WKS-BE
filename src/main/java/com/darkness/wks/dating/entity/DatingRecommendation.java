package com.darkness.wks.dating.entity;

import com.darkness.wks.dating.DatingUnlockField;
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

    @Column(name = "reason_content", columnDefinition = "text")
    private String reasonContent;

    // 정보 해금 상태 (plan.md §8.5·§9.1, 2026-09-26). 이 행은 (조회자, 후보) 쌍에 한 번만 만들어지고
    // 재추천되지 않으므로 여기에 저장해도 재사용에 안전하다 — 별도 해금 테이블을 두지 않는다.
    @Column(name = "photo_unlocked", nullable = false)
    private boolean photoUnlocked;

    @Column(name = "name_unlocked", nullable = false)
    private boolean nameUnlocked;

    @Column(name = "department_unlocked", nullable = false)
    private boolean departmentUnlocked;

    @Column(name = "reason_unlocked", nullable = false)
    private boolean reasonUnlocked;

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

    public void unlock(DatingUnlockField field) {
        switch (field) {
            case PHOTO -> photoUnlocked = true;
            case NAME -> nameUnlocked = true;
            case DEPARTMENT -> departmentUnlocked = true;
            case REASON -> reasonUnlocked = true;
        }
    }

    public boolean isUnlocked(DatingUnlockField field) {
        return switch (field) {
            case PHOTO -> photoUnlocked;
            case NAME -> nameUnlocked;
            case DEPARTMENT -> departmentUnlocked;
            case REASON -> reasonUnlocked;
        };
    }
}
