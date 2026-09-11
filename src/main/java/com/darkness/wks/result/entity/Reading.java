package com.darkness.wks.result.entity;

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
import com.darkness.wks.saju.ReadingCategory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "reading", uniqueConstraints = @UniqueConstraint(columnNames = {"result_id", "category"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false)
    private Result result;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private ReadingCategory category;

    @Column(name = "score", nullable = false)
    private Short score;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Reading(Result result, ReadingCategory category, Short score, String content) {
        this.result = result;
        this.category = category;
        this.score = score;
        this.content = content;
    }
}
