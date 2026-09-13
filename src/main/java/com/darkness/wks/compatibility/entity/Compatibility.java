package com.darkness.wks.compatibility.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.darkness.wks.result.entity.Result;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "compatibility", uniqueConstraints = @UniqueConstraint(columnNames = {"origin_id", "guest_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Compatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_id", nullable = false)
    private Result origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Result guest;

    @Column(name = "score", nullable = false)
    private Short score;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 10, nullable = false)
    private CompatibilityTier tier;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Compatibility(Result origin, Result guest, Short score, CompatibilityTier tier) {
        this.origin = origin;
        this.guest = guest;
        this.score = score;
        this.tier = tier;
    }
}
