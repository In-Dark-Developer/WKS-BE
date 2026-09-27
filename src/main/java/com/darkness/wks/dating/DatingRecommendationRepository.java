package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingRecommendation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatingRecommendationRepository extends JpaRepository<DatingRecommendation, Long> {

    @Query("SELECT r FROM DatingRecommendation r JOIN FETCH r.candidate WHERE r.viewerMemberId = :memberId")
    List<DatingRecommendation> findAllByViewerMemberId(Long memberId);

    @Query("SELECT r FROM DatingRecommendation r WHERE r.viewerMemberId IN :viewerMemberIds "
            + "AND r.candidate.id IN :candidateIds")
    List<DatingRecommendation> findForRequestPairs(List<Long> viewerMemberIds, List<UUID> candidateIds);

    boolean existsByViewerMemberIdAndCandidateIdAndActiveTrue(Long viewerMemberId, UUID candidateId);

    @Query("SELECT r FROM DatingRecommendation r JOIN FETCH r.candidate "
            + "WHERE r.viewerMemberId = :viewerMemberId AND r.candidate.id = :candidateId AND r.active = true")
    Optional<DatingRecommendation> findActiveWithCandidate(Long viewerMemberId, UUID candidateId);

    @Modifying
    @Transactional
    @Query("UPDATE DatingRecommendation r SET r.reasonContent = :content "
            + "WHERE r.id = :id AND r.reasonContent IS NULL")
    int saveReasonIfAbsent(Long id, String content);

    @Query("SELECT r.reasonContent FROM DatingRecommendation r WHERE r.id = :id")
    Optional<String> findReasonContentById(Long id);
}
