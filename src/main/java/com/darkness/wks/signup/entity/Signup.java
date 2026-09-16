package com.darkness.wks.signup.entity;

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
import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.result.entity.Result;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "signup")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Signup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private Result result;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10, nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "prefer_gender", length = 10, nullable = false)
    private Gender preferGender;

    // 필수/선택 여부가 기획 미확정 상태(2026-09-15 기준) — 전부 nullable
    @Column(name = "name", length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_method", length = 10)
    private ContactMethod contactMethod;

    @Column(name = "contact_value", length = 100)
    private String contactValue;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "mbti", length = 4)
    private String mbti;

    @Column(name = "bio", length = 500)
    private String bio;

    // 선택값. S3 오브젝트 키만 저장 (2026-09-16 결정, #54)
    @Column(name = "photo_key", length = 255)
    private String photoKey;

    @Column(name = "coupon_issued", nullable = false)
    private boolean couponIssued = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Signup(String email, Result result, Gender gender, Gender preferGender,
                  String name, ContactMethod contactMethod, String contactValue,
                  String department, String mbti, String bio, String photoKey) {
        this.email = email;
        this.result = result;
        this.gender = gender;
        this.preferGender = preferGender;
        this.name = name;
        this.contactMethod = contactMethod;
        this.contactValue = contactValue;
        this.department = department;
        this.mbti = mbti;
        this.bio = bio;
        this.photoKey = photoKey;
    }

    public void issueCoupon() {
        this.couponIssued = true;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public void markVerified(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
