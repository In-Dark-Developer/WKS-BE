package com.darkness.wks.signup;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.signup.entity.EmailVerification;
import com.darkness.wks.signup.entity.Signup;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationRepository emailVerificationRepository;
    private final JavaMailSender mailSender;

    @Value("${app.signup.email-verification-ttl-minutes}")
    private long ttlMinutes;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.frontend.verify-redirect-url}")
    private String verifyRedirectUrl;

    @Transactional
    public EmailVerification issueToken(Signup signup) {
        String token = generateToken();
        Instant expiresAt = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES);
        return emailVerificationRepository.save(new EmailVerification(token, signup, expiresAt));
    }

    // SMTP 발송 실패가 signup 생성 트랜잭션을 롤백시키면 안 되므로, 별도 트랜잭션 경계에서 호출한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendVerificationEmail(String toEmail, String token) {
        // TODO: 메일 제목/본문 및 재발송 정책 구현
        throw new UnsupportedOperationException("EmailVerificationService.sendVerificationEmail is not implemented yet");
    }

    @Transactional
    public void verify(String token) {
        EmailVerification emailVerification = emailVerificationRepository.findById(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        boolean isValid = emailVerification.getUsedAt() == null
                && emailVerification.getExpiresAt().isAfter(Instant.now());
        if (!isValid) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        emailVerification.markUsed(Instant.now());
    }

    private String generateToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
