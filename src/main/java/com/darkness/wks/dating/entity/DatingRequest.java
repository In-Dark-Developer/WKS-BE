package com.darkness.wks.dating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.UUID;

@Entity
@Table(name = "dating_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatingRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_profile_id", nullable = false, updatable = false)
    private DatingProfile sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_profile_id", nullable = false, updatable = false)
    private DatingProfile recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private DatingRequestStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public DatingRequest(DatingProfile sender, DatingProfile recipient) {
        this.id = UUID.randomUUID();
        this.sender = sender;
        this.recipient = recipient;
        this.status = DatingRequestStatus.PENDING;
    }

    public void accept(Instant time) {
        status = DatingRequestStatus.ACCEPTED;
        respondedAt = time;
    }

    public void reject(Instant time) {
        status = DatingRequestStatus.REJECTED;
        respondedAt = time;
    }

    public void cancel(Instant time) {
        status = DatingRequestStatus.CANCELLED;
        respondedAt = time;
    }
}
