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

    List<Result> findAllByMemberIdIn(List<Long> memberIds);

    boolean existsByMemberId(Long memberId);

    /** 아직 아무 회원에도 연결되지 않은 결과만 대상으로 한다 — 로그인 시 "저장" 연결을 시도할 때 쓴다 */
    Optional<Result> findByIdAndMemberIdIsNull(UUID id);

    /**
     * 회원 행을 잠그고 존재하면 id 를 돌려준다. 같은 회원이 결과를 동시에 만들 때(버튼 연타) "계정 결과 없음"
     * 확인과 연결을 직렬화한다 — 안 그러면 둘 다 연결을 시도해 부분 UNIQUE(uq_result_member)에 걸려 500 이 난다.
     * {@code member} 엔티티를 참조하지 않으려고 네이티브로 쓴다(Result 가 member 패키지를 모르게, architecture.md §4).
     */
    @Query(value = "SELECT id FROM member WHERE id = :memberId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockMember(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Result r
            WHERE r.id IN :ids
            ORDER BY r.id
            """)
    List<Result> findAllByIdForUpdate(@Param("ids") List<UUID> ids);
}
