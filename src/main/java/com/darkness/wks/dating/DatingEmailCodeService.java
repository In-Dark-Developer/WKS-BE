package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.entity.DatingEmailCode;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 소개팅 학교메일 6자리 코드 인증. 프로필 등록 전에 인증을 끝내게 하려고 매직링크(V21) 대신 쓴다 —
 * 링크는 다른 탭에서 열려 작성 중인 폼이 인증 완료를 알 수 없지만, 코드는 같은 화면에서 입력한다.
 * <p>
 * 6자리는 추측 공간이 작아서 코드당 실패 횟수와 발송 횟수를 같이 막아야 한다. 둘 중 하나만 있으면
 * "재발송 → 5번 시도"를 반복해 남의 학교메일을 인증할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DatingEmailCodeService {

    static final Duration TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration SEND_WINDOW = Duration.ofHours(24);
    static final int MAX_SENDS_PER_WINDOW = 10;
    static final int MAX_FAILED_ATTEMPTS = 5;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // 문구는 기획 확정 전 임시값 — DatingEmailVerificationService 와 같은 처리
    private static final String SUBJECT = "[동국대 소개팅] 학교 이메일 인증 코드";

    private final DatingEmailCodeRepository codeRepository;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFrom;

    /** 평문 코드는 메일로만 나가고 DB 에는 해시만 남는다 */
    public record IssuedCode(String code, Instant expiresAt, Instant resendAvailableAt) {
    }

    @Transactional
    public IssuedCode issue(Long memberId, String email) {
        Instant now = Instant.now();
        String code = generateCode();
        Instant expiresAt = now.plus(TTL);
        DatingEmailCode existing = codeRepository.findForUpdate(memberId).orElse(null);
        if (existing == null) {
            try {
                codeRepository.saveAndFlush(new DatingEmailCode(memberId, email, hash(code), expiresAt, now));
            } catch (DataIntegrityViolationException exception) {
                // 같은 회원의 첫 발송 두 건이 동시에 들어왔다 — 한 건은 이미 나갔으니 쿨다운과 같게 취급한다
                throw new BusinessException(ErrorCode.EMAIL_CODE_RATE_LIMITED);
            }
        } else {
            if (existing.getLastSentAt().plus(RESEND_COOLDOWN).isAfter(now)) {
                throw new BusinessException(ErrorCode.EMAIL_CODE_RATE_LIMITED);
            }
            boolean windowExpired = !existing.getSendWindowStart().plus(SEND_WINDOW).isAfter(now);
            if (!windowExpired && existing.getSendCount() >= MAX_SENDS_PER_WINDOW) {
                throw new BusinessException(ErrorCode.EMAIL_CODE_RATE_LIMITED);
            }
            existing.reissue(email, hash(code), expiresAt, now, windowExpired);
        }
        return new IssuedCode(code, expiresAt, now.plus(RESEND_COOLDOWN));
    }

    /**
     * 메일이 안 나갔으면 코드를 지운다 — 남겨두면 받지도 못한 코드 때문에 쿨다운에 걸려 바로 재시도를 못 한다.
     * 발송이 실패했으니 지워서 풀리는 발송 한도도 실제로 소비된 적이 없다.
     */
    @Transactional
    public void discard(Long memberId) {
        codeRepository.deleteById(memberId);
    }

    // SMTP 가 느려서(수백 ms~수 초) DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 호출한다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendCodeEmail(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(SUBJECT);
            helper.setText("""
                    학교 이메일 인증 코드는 아래와 같다. %d분 안에 입력해라.

                    %s

                    요청한 적이 없으면 이 메일은 무시해도 된다.
                    """.formatted(TTL.toMinutes(), code));
            mailSender.send(message);
        } catch (Exception exception) {
            throw new MailSendFailedException(exception);
        }
    }

    /**
     * 틀린 입력도 실패 횟수를 남겨야 하므로 BusinessException 으로 롤백하지 않는다.
     * 만료·실패 초과·다른 이메일은 전부 같은 INVALID_EMAIL_CODE 로 돌려 어느 조건에 걸렸는지 알려주지 않는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(Long memberId, String email, String code) {
        DatingEmailCode emailCode = codeRepository.findForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_EMAIL_CODE));
        if (emailCode.isVerifiedFor(email)) {
            // 인증 버튼 중복 클릭·새로고침 재전송은 성공으로 본다
            return;
        }
        if (!emailCode.getEmail().equals(email)
                || !emailCode.getExpiresAt().isAfter(Instant.now())
                || emailCode.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_CODE);
        }
        if (!MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.US_ASCII),
                emailCode.getCodeHash().getBytes(StandardCharsets.US_ASCII))) {
            emailCode.recordFailure();
            throw new BusinessException(ErrorCode.INVALID_EMAIL_CODE);
        }
        emailCode.markVerified(Instant.now());
    }

    public boolean isVerified(Long memberId, String email) {
        return codeRepository.findById(memberId)
                .map(emailCode -> emailCode.isVerifiedFor(email))
                .orElse(false);
    }

    private static String generateCode() {
        return "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
    }

    private static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // jakarta.mail.MessagingException(checked)과 MailException(unchecked)을 호출부에서 한 타입으로 잡기 위한 래퍼
    public static class MailSendFailedException extends RuntimeException {
        public MailSendFailedException(Throwable cause) {
            super(cause);
        }
    }
}
