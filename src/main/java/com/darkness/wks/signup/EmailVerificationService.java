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
    // 개인 지메일로 발송해 스팸으로 분류되기 쉽다 — 반말·명령조 문구를 피하고 서비스명을 앞에 둔다
    private static final String SUBJECT = "[운명도 꿰어야 사랑이다] 사전신청 이메일 인증을 완료해 주세요";

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
                    안녕하세요, 동국대 축제 '운명도 꿰어야 사랑이다'입니다.
                    사전신청해 주셔서 감사합니다. 아래 링크를 누르면 이메일 인증이 끝나요.

                    %s

                    링크는 %d분 동안 쓸 수 있어요.
                    직접 신청하신 적이 없다면 이 메일은 무시하셔도 됩니다.
                    """.formatted(verifyLink, ttlMinutes));
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
