package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingRequest;
import com.darkness.wks.dating.entity.DatingRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatingRequestRepository extends JpaRepository<DatingRequest, UUID> {

    @Query("""
            SELECT COUNT(r) > 0 FROM DatingRequest r
            WHERE r.status <> com.darkness.wks.dating.entity.DatingRequestStatus.CANCELLED
              AND ((r.sender.id = :first AND r.recipient.id = :second)
                OR (r.sender.id = :second AND r.recipient.id = :first))
            """)
    boolean existsBetween(UUID first, UUID second);

    @Query("SELECT r FROM DatingRequest r JOIN FETCH r.sender s JOIN FETCH s.photo "
            + "JOIN FETCH r.recipient p JOIN FETCH p.photo "
            + "WHERE s.memberId = :memberId ORDER BY r.createdAt DESC")
    List<DatingRequest> findSent(Long memberId);

    @Query("SELECT r FROM DatingRequest r JOIN FETCH r.sender s JOIN FETCH s.photo "
            + "JOIN FETCH r.recipient p JOIN FETCH p.photo "
            + "WHERE p.memberId = :memberId AND r.status <> :cancelled ORDER BY r.createdAt DESC")
    List<DatingRequest> findReceived(Long memberId, DatingRequestStatus cancelled);

    @Query("SELECT r FROM DatingRequest r JOIN FETCH r.sender JOIN FETCH r.recipient WHERE r.id = :id")
    Optional<DatingRequest> findWithProfiles(UUID id);

    @Modifying
    @Transactional
    @Query("UPDATE DatingRequest r SET r.recipientReason = :content "
            + "WHERE r.id = :id AND r.recipientReason IS NULL")
    int saveRecipientReasonIfAbsent(UUID id, String content);
}
