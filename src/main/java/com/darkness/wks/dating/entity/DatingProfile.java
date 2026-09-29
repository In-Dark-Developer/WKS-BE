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

    // 운영자가 내린 상태 (#147). 삭제와 달리 되돌릴 수 있고 요청·해금 기록은 남는다
    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DatingProfile(Long memberId, String email, String name,
                         ContactMethod contactMethod, String contactValue, String department,
                         String mbti, String bio, DatingPhoto photo) {
        this.id = UUID.randomUUID();
        this.memberId = memberId;
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

    /** 추천 풀·기존 카드·요청 수락이 모두 이 한 곳으로 자격을 판정한다. */
    public boolean isEligible() {
        return verifiedAt != null && deactivatedAt == null;
    }

    public void deactivate(Instant time) {
        if (deactivatedAt == null) {
            deactivatedAt = time;
        }
    }

    public void activate() {
        deactivatedAt = null;
    }

    /** 운영자 수정. 호출 쪽이 "바뀌지 않은 필드는 현재 값"으로 채워서 넘긴다. */
    public void edit(String email, String name, ContactMethod contactMethod, String contactValue,
                     String department, String mbti, String bio) {
        this.email = email;
        this.name = name;
        this.contactMethod = contactMethod;
        this.contactValue = contactValue;
        this.department = department;
        this.mbti = mbti;
        this.bio = bio;
    }

    public void changePhoto(DatingPhoto photo) {
        this.photo = photo;
    }
}
