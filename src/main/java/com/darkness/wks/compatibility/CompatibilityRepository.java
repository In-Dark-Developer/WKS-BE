package com.darkness.wks.compatibility;

import com.darkness.wks.compatibility.entity.Compatibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    @Query("""
            SELECT compatibility
            FROM Compatibility compatibility
            JOIN FETCH compatibility.origin
            JOIN FETCH compatibility.guest
            WHERE compatibility.id = :id
            """)
    Optional<Compatibility> findByIdWithResults(@Param("id") Long id);

    /**
     * 이유가 아직 없을 때만 채운다. 두 사람이 동시에 처음 열면 LLM 을 두 번 부르지만 저장은 먼저 온 쪽만 되고,
     * 진 쪽은 0 을 받아 저장된 것을 다시 읽는다 — 그래서 둘이 같은 글을 본다. 잠금으로 막으면 LLM 30초 동안 DB 연결을 잡아 둬서 이렇게 한다
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Compatibility compatibility
            SET compatibility.reasonWhy = :why,
                compatibility.reasonTogether = :together,
                compatibility.reasonConflict = :conflict
            WHERE compatibility.id = :id
              AND compatibility.reasonWhy IS NULL
            """)
    int saveReasonIfAbsent(@Param("id") Long id,
                           @Param("why") String why,
                           @Param("together") String together,
                           @Param("conflict") String conflict);
}
