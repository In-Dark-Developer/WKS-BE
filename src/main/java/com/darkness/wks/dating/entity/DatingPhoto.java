package com.darkness.wks.dating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dating_photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatingPhoto {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "object_key", nullable = false, unique = true, updatable = false, length = 255)
    private String objectKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DatingPhoto(Long memberId, String objectKey) {
        id = UUID.randomUUID();
        this.memberId = memberId;
        this.objectKey = objectKey;
    }
}
