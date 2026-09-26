package com.darkness.wks.signup;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.signup.dto.SignupReapplyResponse;
import com.darkness.wks.signup.entity.Signup;
import com.darkness.wks.signup.entity.SignupReapplyInvite;
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
import java.util.List;

/**
 * 기존 사전신청자에게 보내는 재신청 초대(1회성 캠페인). 사전신청 때는 로그인이 없어서 그때 만든 사주 결과가
 * 어느 계정에도 붙어 있지 않다 — 초대 링크로 들어와 카카오 로그인하면 그 결과가 계정에 연결된다.
 * <p>
 * 초대는 학교메일 인증이 아니다. 사전신청 이메일은 gmail 등 아무 주소였고 인증도 안 됐으므로, 학교메일
 * 인증·사진 등록은 로그인 뒤 일반 소개팅 신청과 똑같이 한다(코드 인증, dating/).
 * 토큰은 소비하지 않는다 — 결과가 계정에 연결되면 캠페인 대상에서 빠지는 것으로 완료를 판단한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignupReapplyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // 문구는 기획 확정 전 임시값 — EmailVerificationService 와 같은 처리
    private static final String SUBJECT = "[동국대 소개팅] 사전신청 고맙다 — 사주 결과 이어서 소개팅 신청하러 와라";

    private final SignupRepository signupRepository;
    private final SignupReapplyInviteRepository inviteRepository;
    private final JavaMailSender mailSender;

    @Value("${app.signup.reapply-invite-ttl-hours}")
    private long ttlHours;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.frontend.reapply-url}")
    private String reapplyUrl;

    /** 초대 메일을 보낼 대상. 엔티티를 트랜잭션 밖으로 내보내지 않는다 */
    public record ReapplyTarget(Long signupId, String email) {
    }

    public List<ReapplyTarget> findTargets() {
        return signupRepository.findReapplyTargets(Instant.now()).stream()
                .map(signup -> new ReapplyTarget(signup.getId(), signup.getEmail()))
                .toList();
    }

    @Transactional
    public SignupReapplyInvite issueInvite(Long signupId) {
        Signup signup = signupRepository.getReferenceById(signupId);
        Instant expiresAt = Instant.now().plus(ttlHours, ChronoUnit.HOURS);
        return inviteRepository.save(new SignupReapplyInvite(generateToken(), signup, expiresAt));
    }

    /** 메일 발송이 실패한 초대는 지운다 — 남겨두면 유효한 초대로 보여서 다음 실행 때 재시도 대상에서 빠진다 */
    @Transactional
    public void discardInvite(String token) {
        inviteRepository.deleteById(token);
    }

    // SMTP 발송이 느려서(수백 ms~수 초) 커넥션을 붙잡지 않도록 트랜잭션 밖에서 호출한다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendInviteEmail(String toEmail, String token) {
        String link = reapplyUrl + "?token=" + token;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(SUBJECT);
            helper.setText("""
                    사전신청 고맙다. 정식 소개팅 신청이 열렸다.

                    아래 링크에서 카카오 로그인하면 사전신청 때 본 사주 결과가 계정에 그대로 이어진다.
                    그다음 학교메일(@dgu.ac.kr) 인증과 사진 등록을 하면 소개팅 신청이 끝난다.
                    사전신청 때 적은 내용은 신청서에 미리 채워져 있다. 링크는 %d시간 동안 유효하다.

                    %s
                    """.formatted(ttlHours, link));
            mailSender.send(message);
        } catch (Exception exception) {
            throw new InviteMailFailedException(exception);
        }
    }

    /** 재신청 폼 자동 채움 (`GET /api/signups/reapply`). 토큰은 여기서 소비하지 않는다 */
    public SignupReapplyResponse prefill(String token) {
        return SignupReapplyResponse.from(usableInvite(token).getSignup());
    }

    private SignupReapplyInvite usableInvite(String token) {
        SignupReapplyInvite invite = inviteRepository.findById(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (!invite.isUsable(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return invite;
    }

    private String generateToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static class InviteMailFailedException extends RuntimeException {
        public InviteMailFailedException(Throwable cause) {
            super(cause);
        }
    }
}
