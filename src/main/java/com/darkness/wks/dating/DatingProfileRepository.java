package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatingProfileRepository extends JpaRepository<DatingProfile, UUID> {

    Optional<DatingProfile> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);

    boolean existsByEmailAndMemberIdNot(String email, Long memberId);

    @Query("SELECT p FROM DatingProfile p WHERE p.verifiedAt IS NOT NULL AND p.deactivatedAt IS NULL")
    List<DatingProfile> findEligible();

    // 일괄 안내 메일 대상 (#159). 운영자가 내린 프로필만 뺀다 — 추천 자격(findEligible)과 기준이 다르다
    List<DatingProfile> findByDeactivatedAtIsNull();

    // 관리자 작업은 트랜잭션 밖에서 S3 를 만지므로 사진을 함께 가져와 LAZY 예외를 피한다 (DatingAdminService)
    @Query("SELECT p FROM DatingProfile p JOIN FETCH p.photo WHERE p.id = :id")
    Optional<DatingProfile> findWithPhotoById(UUID id);

    @Query("SELECT p FROM DatingProfile p JOIN FETCH p.photo WHERE p.email = :email")
    Optional<DatingProfile> findWithPhotoByEmail(String email);
}
