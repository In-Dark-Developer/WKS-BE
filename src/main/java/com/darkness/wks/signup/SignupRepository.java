package com.darkness.wks.signup;

import com.darkness.wks.signup.entity.Signup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SignupRepository extends JpaRepository<Signup, Long> {

    boolean existsByEmail(String email);

    Optional<Signup> findByEmail(String email);
}
