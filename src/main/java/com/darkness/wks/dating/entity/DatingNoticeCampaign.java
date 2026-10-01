package com.darkness.wks.dating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 일괄 안내 메일 예약 (#159). 상태 전이는 동시에 두 번 잡히지 않도록 전부
 * {@link com.darkness.wks.dating.DatingNoticeCampaignRepository} 의 조건부 쿼리로 한다. 이 엔티티는 읽기용이다.
 */
@Entity
@Table(name = "dating_notice_campaign")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatingNoticeCampaign {

    public enum State {
        SCHEDULED, STARTED, EXPIRED
    }

    @Id
    @Column(name = "campaign_key", length = 50)
    private String campaignKey;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "send_at", nullable = false)
    private Instant sendAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 10)
    private State state;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
