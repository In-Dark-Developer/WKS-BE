package com.darkness.wks.dating.entity;

import com.darkness.wks.common.ContactMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dating_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatingProfile {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false, unique = true, updatable = false)
    private Long memberId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_method", nullable = false, length = 10)
    private ContactMethod contactMethod;

    @Column(name = "contact_value", nullable = false, length = 100)
    private String contactValue;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "mbti", nullable = false, length = 4)
    private String mbti;

    @Column(name = "bio", nullable = false, length = 500)
    private String bio;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false, unique = true)
    private DatingPhoto photo;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DatingProfile(Long memberId, String email, String name,
                         ContactMethod contactMethod, String contactValue, String department,
                         String mbti, String bio, DatingPhoto photo) {
        this.id = UUID.randomUUID();
        this.memberId = memberId;
        update(email, name, contactMethod, contactValue, department, mbti, bio, photo);
    }

    public void update(String email, String name, ContactMethod contactMethod, String contactValue,
                       String department, String mbti, String bio, DatingPhoto photo) {
        if (this.email != null && !this.email.equalsIgnoreCase(email)) {
            verifiedAt = null;
        }
        this.email = email;
        this.name = name;
        this.contactMethod = contactMethod;
        this.contactValue = contactValue;
        this.department = department;
        this.mbti = mbti;
        this.bio = bio;
        this.photo = photo;
    }

    public void markVerified(Instant time) {
        verifiedAt = time;
    }

    public void markMatched(Instant time) {
        matchedAt = time;
    }

    public boolean isEligible() {
        return verifiedAt != null && matchedAt == null;
    }
}
