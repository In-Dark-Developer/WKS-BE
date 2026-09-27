package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingEmailCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DatingEmailCodeRepository extends JpaRepository<DatingEmailCode, Long> {

    // 버튼 연타·동시 입력에서 쿨다운 검사와 실패 횟수 증가가 겹쳐 뚫리지 않도록 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DatingEmailCode c WHERE c.memberId = :memberId")
    Optional<DatingEmailCode> findForUpdate(@Param("memberId") Long memberId);
}
