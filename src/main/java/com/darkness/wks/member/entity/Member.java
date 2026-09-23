package com.darkness.wks.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 로그인 회원. 카카오 프로필(닉네임·이메일 등)은 저장하지 않는다 — 동의항목을 받지 않고
 * 카카오 회원번호(kakao_id)만 식별자로 쓴다 (architecture.md §4 원칙 3).
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "kakao_id", nullable = false, unique = true)
    private Long kakaoId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    public Member(Long kakaoId) {
        this.kakaoId = kakaoId;
        this.lastLoginAt = Instant.now();
    }

    public void touchLogin() {
        this.lastLoginAt = Instant.now();
    }
}
