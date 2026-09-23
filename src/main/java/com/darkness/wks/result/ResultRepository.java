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

    /** 로그인 회원이 저장한 결과 — 계정당 최대 1개(부분 unique, V11) */
    Optional<Result> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);

    /** 아직 아무 회원에도 연결되지 않은 결과만 대상으로 한다 — 로그인 시 "저장" 연결을 시도할 때 쓴다 */
    Optional<Result> findByIdAndMemberIdIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Result r
            WHERE r.id IN :ids
            ORDER BY r.id
            """)
    List<Result> findAllByIdForUpdate(@Param("ids") List<UUID> ids);
}
