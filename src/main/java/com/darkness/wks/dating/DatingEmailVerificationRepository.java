package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingEmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatingEmailVerificationRepository extends JpaRepository<DatingEmailVerification, String> {
}
