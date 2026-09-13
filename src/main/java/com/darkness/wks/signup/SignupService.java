package com.darkness.wks.signup;

import com.darkness.wks.result.ResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// TODO: 소개팅 사전등록 신청/이메일 인증 처리 로직 구현
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignupService {

    private final SignupRepository signupRepository;
    private final ResultRepository resultRepository;
    private final EmailVerificationService emailVerificationService;
}
