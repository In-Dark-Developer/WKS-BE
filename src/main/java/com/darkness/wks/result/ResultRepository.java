package com.darkness.wks.result;

import com.darkness.wks.result.entity.Result;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultRepository extends JpaRepository<Result, UUID> {

    Optional<Result> findByShareId(UUID shareId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Result r
            WHERE r.id IN :ids
            ORDER BY r.id
            """)
    List<Result> findAllByIdForUpdate(@Param("ids") List<UUID> ids);
}
