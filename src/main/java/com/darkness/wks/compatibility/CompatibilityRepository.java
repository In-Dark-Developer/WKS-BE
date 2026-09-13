package com.darkness.wks.compatibility;

import com.darkness.wks.compatibility.entity.Compatibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompatibilityRepository extends JpaRepository<Compatibility, Long> {

    @Query("""
            SELECT compatibility
            FROM Compatibility compatibility
            JOIN FETCH compatibility.origin
            JOIN FETCH compatibility.guest
            WHERE compatibility.origin.id = :resultId
               OR compatibility.guest.id = :resultId
            ORDER BY compatibility.createdAt DESC
            """)
    List<Compatibility> findAllByResultIdOrderByCreatedAtDesc(@Param("resultId") UUID resultId);

    @Query("""
            SELECT compatibility
            FROM Compatibility compatibility
            JOIN FETCH compatibility.origin
            JOIN FETCH compatibility.guest
            WHERE (compatibility.origin.id = :firstId AND compatibility.guest.id = :secondId)
               OR (compatibility.origin.id = :secondId AND compatibility.guest.id = :firstId)
            """)
    Optional<Compatibility> findByResultPair(
            @Param("firstId") UUID firstId,
            @Param("secondId") UUID secondId
    );
}
