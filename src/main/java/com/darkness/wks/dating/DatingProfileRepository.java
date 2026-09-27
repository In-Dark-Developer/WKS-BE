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

    @Query("SELECT p FROM DatingProfile p WHERE p.verifiedAt IS NOT NULL")
    List<DatingProfile> findEligible();
}
