package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DatingRequestRepository extends JpaRepository<DatingRequest, UUID> {

    @Query("""
            SELECT COUNT(r) > 0 FROM DatingRequest r
            WHERE (r.sender.id = :first AND r.recipient.id = :second)
               OR (r.sender.id = :second AND r.recipient.id = :first)
            """)
    boolean existsBetween(UUID first, UUID second);

    @Query("SELECT r FROM DatingRequest r JOIN FETCH r.sender JOIN FETCH r.recipient "
            + "WHERE r.sender.memberId = :memberId ORDER BY r.createdAt DESC")
    List<DatingRequest> findSent(Long memberId);

    @Query("SELECT r FROM DatingRequest r JOIN FETCH r.sender JOIN FETCH r.recipient "
            + "WHERE r.recipient.memberId = :memberId ORDER BY r.createdAt DESC")
    List<DatingRequest> findReceived(Long memberId);
}
