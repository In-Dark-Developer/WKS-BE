package com.darkness.wks.signup;

import com.darkness.wks.signup.entity.Signup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SignupRepository extends JpaRepository<Signup, Long> {

    boolean existsByEmail(String email);

    /** 재발송은 트랜잭션 없이 돌며 결과의 계정 연결 여부를 보므로 LAZY 인 result 를 함께 읽는다 */
    @Query("SELECT s FROM Signup s LEFT JOIN FETCH s.result WHERE s.email = :email")
    Optional<Signup> findWithResultByEmail(@Param("email") String email);

    /**
     * 재신청 초대 대상. 초대의 목적은 사전신청 때 만든 사주 결과를 카카오 계정에 잇는 것이라
     * 결과가 있고 아직 어느 계정에도 연결되지 않은 신청자만 고른다 — 연결됐으면 목적을 이룬 것이다.
     * 사전신청 이메일은 학교메일이 아니고 인증도 안 된 경우가 많아 도메인은 거르지 않는다(학교메일
     * 인증은 소개팅 등록 때 코드로 따로 한다). 아직 유효한 초대가 있으면 다시 보내지 않는다.
     */
    @Query("""
            SELECT s FROM Signup s
            WHERE s.result IS NOT NULL
              AND s.result.memberId IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM SignupReapplyInvite i
                  WHERE i.signup = s AND i.expiresAt > :now
              )
            ORDER BY s.id
            """)
    List<Signup> findReapplyTargets(@Param("now") Instant now);
}
