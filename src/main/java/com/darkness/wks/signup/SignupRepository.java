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

    Optional<Signup> findByEmail(String email);

    /**
     * 재신청 초대 대상. 사주 결과가 붙어 있어야 카카오 로그인 때 계정에 연결할 수 있고(결과 없는 신청자는
     * 새로 신청하는 게 빠르다), 아직 유효하거나 이미 완료된 초대가 있으면 다시 보내지 않는다.
     * {@code emailSuffix} 는 인덱스 없는 LIKE 라 넓게 걸러지므로(예: {@code fakedgu.ac.kr}) 호출부가
     * 도메인을 한 번 더 정확히 검사한다.
     */
    @Query("""
            SELECT s FROM Signup s
            WHERE s.result IS NOT NULL
              AND LOWER(s.email) LIKE CONCAT('%', :emailSuffix)
              AND NOT EXISTS (
                  SELECT 1 FROM SignupReapplyInvite i
                  WHERE i.signup = s AND (i.usedAt IS NOT NULL OR i.expiresAt > :now)
              )
            ORDER BY s.id
            """)
    List<Signup> findReapplyTargets(@Param("emailSuffix") String emailSuffix, @Param("now") Instant now);
}
