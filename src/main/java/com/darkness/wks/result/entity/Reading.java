package com.darkness.wks.result.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reading")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reading {

    @Id
    @Column(name = "result_id")
    private UUID resultId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private Result result;

    @Column(name = "destiny_content", nullable = false, columnDefinition = "TEXT")
    private String destinyContent;

    @Column(name = "marriage_score", nullable = false)
    private Short marriageScore;

    @Column(name = "marriage_content", nullable = false, columnDefinition = "TEXT")
    private String marriageContent;

    @Column(name = "children_score", nullable = false)
    private Short childrenScore;

    @Column(name = "children_content", nullable = false, columnDefinition = "TEXT")
    private String childrenContent;

    @Column(name = "love_score", nullable = false)
    private Short loveScore;

    @Column(name = "love_content", nullable = false, columnDefinition = "TEXT")
    private String loveContent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Reading(
            Result result,
            String destinyContent,
            int marriageScore,
            String marriageContent,
            int childrenScore,
            String childrenContent,
            int loveScore,
            String loveContent
    ) {
        this.result = result;
        this.destinyContent = destinyContent;
        this.marriageScore = (short) marriageScore;
        this.marriageContent = marriageContent;
        this.childrenScore = (short) childrenScore;
        this.childrenContent = childrenContent;
        this.loveScore = (short) loveScore;
        this.loveContent = loveContent;
    }
}
