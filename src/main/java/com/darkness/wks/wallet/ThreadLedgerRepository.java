package com.darkness.wks.wallet;

import com.darkness.wks.wallet.entity.ThreadLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThreadLedgerRepository extends JpaRepository<ThreadLedger, Long> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ThreadLedger t WHERE t.memberId = :memberId")
    int sumAmountByMemberId(@Param("memberId") Long memberId);

    boolean existsByMemberIdAndReasonAndRefId(Long memberId, LedgerReason reason, String refId);

    long countByMemberIdAndReasonAndRefIdStartingWith(Long memberId, LedgerReason reason, String refIdPrefix);

    /**
     * 같은 회원의 지급·차감을 트랜잭션 동안 직렬화한다. {@code member} 테이블을 잠그지 않는 이유는
     * wallet 이 다른 도메인 엔티티를 참조하지 않기 때문이다(architecture.md §3 의존 방향 — 원장이
     * 가장 아래). PostgreSQL 트랜잭션 advisory lock 은 커밋·롤백 시 자동 해제된다.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(:memberId)", nativeQuery = true)
    void lockMember(@Param("memberId") Long memberId);
}
