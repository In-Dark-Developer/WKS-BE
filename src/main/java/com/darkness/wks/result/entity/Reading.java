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

    @Column(name = "destiny_title", length = 100, nullable = false)
    private String destinyTitle;

    @Column(name = "destiny_content", nullable = false, columnDefinition = "TEXT")
    private String destinyContent;

    @Column(name = "marriage_grade", length = 10, nullable = false)
    private String marriageGrade;

    @Column(name = "marriage_content", nullable = false, columnDefinition = "TEXT")
    private String marriageContent;

    @Column(name = "children_grade", length = 10, nullable = false)
    private String childrenGrade;

    @Column(name = "children_content", nullable = false, columnDefinition = "TEXT")
    private String childrenContent;

    @Column(name = "love_grade", length = 10, nullable = false)
    private String loveGrade;

    @Column(name = "love_content", nullable = false, columnDefinition = "TEXT")
    private String loveContent;

    @Column(name = "lucky_item", length = 100, nullable = false)
    private String luckyItem;

    @Column(name = "lucky_place", length = 100, nullable = false)
    private String luckyPlace;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Reading(
            Result result,
            String destinyTitle,
            String destinyContent,
            String marriageGrade,
            String marriageContent,
            String childrenGrade,
            String childrenContent,
            String loveGrade,
            String loveContent,
            String luckyItem,
            String luckyPlace
    ) {
        this.result = result;
        this.destinyTitle = destinyTitle;
        this.destinyContent = destinyContent;
        this.marriageGrade = marriageGrade;
        this.marriageContent = marriageContent;
        this.childrenGrade = childrenGrade;
        this.childrenContent = childrenContent;
        this.loveGrade = loveGrade;
        this.loveContent = loveContent;
        this.luckyItem = luckyItem;
        this.luckyPlace = luckyPlace;
    }
}
