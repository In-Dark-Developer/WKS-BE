package com.darkness.wks.dating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 일괄 안내 메일의 수신자별 발송 기록 (#159). 쓰기는 선점이 원자적이어야 해서 전부
 * {@link com.darkness.wks.dating.DatingNoticeMailRepository} 의 네이티브 쿼리로 한다. 이 엔티티는 스키마 검증용이다.
 */
@Entity
@Table(name = "dating_notice_mail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatingNoticeMail {

    public enum Status {
        SENDING, SENT, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_key", nullable = false, updatable = false, length = 50)
    private String campaignKey;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private UUID profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
