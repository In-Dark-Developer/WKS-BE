package com.darkness.wks.signup;

import com.darkness.wks.signup.entity.Signup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignupRepository extends JpaRepository<Signup, Long> {
}
