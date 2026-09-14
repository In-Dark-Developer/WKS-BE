package com.darkness.wks.signup;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.signup.entity.EmailVerification;
import com.darkness.wks.signup.entity.Signup;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // 문구는 기획(영채) 확정 전 임시값 — docs/api-spec.md destiny.title 과 같은 처리
    private static final String SUBJECT = "[동국대 소개팅] 이메일 인증을 완료해라";

    private final EmailVerificationRepository emailVerificationRepository;
    private final JavaMailSender mailSender;

    @Value("${app.signup.email-verification-ttl-minutes}")
    private long ttlMinutes;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.backend.base-url}")
    private String backendBaseUrl;

    @Transactional
    public EmailVerification issueToken(Signup signup) {
        String token = generateToken();
        Instant expiresAt = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES);
        return emailVerificationRepository.save(new EmailVerification(token, signup, expiresAt));
    }

    // SMTP 발송 실패가 signup 생성 트랜잭션을 롤백시키면 안 되므로, 별도 트랜잭션 경계에서 호출한다.
    // 호출부(SignupService)가 MailException 을 잡아 mailSent=false 로 처리한다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendVerificationEmail(String toEmail, String token) {
        String verifyLink = backendBaseUrl + "/api/signups/verify?token=" + token;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(SUBJECT);
            helper.setText("""
                    아래 링크를 눌러 이메일 인증을 완료해라. 링크는 %d분간 유효하다.

                    %s
                    """.formatted(ttlMinutes, verifyLink));
            mailSender.send(message);
        } catch (Exception exception) {
            log.warn("verification mail send failed", exception);
            throw new MailSendFailedException(exception);
        }
    }

    @Transactional
    public EmailVerification verify(String token) {
        EmailVerification emailVerification = emailVerificationRepository.findById(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        boolean isValid = emailVerification.getUsedAt() == null
                && emailVerification.getExpiresAt().isAfter(Instant.now());
        if (!isValid) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        emailVerification.markUsed(Instant.now());
        return emailVerification;
    }

    // jakarta.mail.MessagingException(checked)과 MailException(unchecked)을 호출부에서 한 타입으로 잡기 위한 래퍼
    public static class MailSendFailedException extends RuntimeException {
        public MailSendFailedException(Throwable cause) {
            super(cause);
        }
    }

    private String generateToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
