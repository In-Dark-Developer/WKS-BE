package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DatingRecommendationRepository extends JpaRepository<DatingRecommendation, Long> {

    @Query("SELECT r FROM DatingRecommendation r JOIN FETCH r.candidate WHERE r.viewerMemberId = :memberId")
    List<DatingRecommendation> findAllByViewerMemberId(Long memberId);

    boolean existsByViewerMemberIdAndCandidateIdAndActiveTrue(Long viewerMemberId, UUID candidateId);
}
