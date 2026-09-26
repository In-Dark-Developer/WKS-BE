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
import java.util.Locale;

/**
 * 기존 사전신청자에게 "사진·학교메일 인증을 미리 마무리해라"고 보내는 재신청 초대(1회성 캠페인).
 * <p>
 * 초대 링크는 프론트 페이지를 가리킨다 — 클릭 한 번으로 끝나는 인증이 아니라 카카오 로그인·사진 업로드를
 * 거쳐야 하므로, 토큰은 그 흐름이 끝날 때({@code POST /api/dating/profile}) 소비된다.
 * 초대 메일을 받은 사람만 그 주소를 쓸 수 있으므로, 완료 시점에 학교메일 인증을 이미 끝난 것으로 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignupReapplyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // 문구는 기획 확정 전 임시값 — EmailVerificationService 와 같은 처리
    private static final String SUBJECT = "[동국대 소개팅] 사전신청 마무리하러 와라 (사진·학교메일 인증)";

    private final SignupRepository signupRepository;
    private final SignupReapplyInviteRepository inviteRepository;
    private final JavaMailSender mailSender;

    @Value("${app.signup.reapply-invite-ttl-hours}")
    private long ttlHours;

    // 캠페인 대상 도메인 하나만 받는다. 소개팅 참여 자체를 막는 검증은 dating/ 의 화이트리스트가 한다
    @Value("${app.signup.reapply-campaign.email-domain}")
    private String campaignDomain;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.frontend.reapply-url}")
    private String reapplyUrl;

    /** 초대 메일을 보낼 대상. 엔티티를 트랜잭션 밖으로 내보내지 않는다 */
    public record ReapplyTarget(Long signupId, String email) {
    }

    public List<ReapplyTarget> findTargets() {
        return signupRepository.findReapplyTargets(campaignDomain.toLowerCase(Locale.ROOT), Instant.now()).stream()
                .filter(signup -> isCampaignDomain(signup.getEmail()))
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
                    사전신청 고맙다. 그 뒤로 사진 등록과 학교메일 인증이 생겨서 한 번 더 부탁한다.

                    아래 링크에서 카카오 로그인하고 사진만 올리면 끝난다. 사전신청 때 적은 내용은 그대로
                    채워져 있고, 사주 결과도 계정에 그대로 이어진다. 링크는 %d시간 동안 유효하다.

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

    /**
     * 초대가 보장하는 이메일. 소개팅 프로필 등록이 이 값과 요청 이메일이 같은지 확인한다 —
     * 초대는 "그 주소의 소유 증명"일 뿐이라 다른 주소의 인증으로 넘겨 쓸 수 없다.
     */
    public String invitedEmail(String token) {
        return usableInvite(token).getSignup().getEmail().trim().toLowerCase(Locale.ROOT);
    }

    /** 소개팅 프로필 등록이 끝나는 트랜잭션 안에서 호출된다 — 등록이 롤백되면 초대도 다시 쓸 수 있어야 한다 */
    @Transactional
    public void consumeInvite(String token) {
        usableInvite(token).markUsed(Instant.now());
    }

    private SignupReapplyInvite usableInvite(String token) {
        SignupReapplyInvite invite = inviteRepository.findById(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (!invite.isUsable(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return invite;
    }

    private boolean isCampaignDomain(String email) {
        String domain = email.trim().toLowerCase(Locale.ROOT);
        int at = domain.lastIndexOf('@');
        domain = at < 0 ? "" : domain.substring(at + 1);
        String allowed = campaignDomain.trim().toLowerCase(Locale.ROOT);
        return domain.equals(allowed) || domain.endsWith("." + allowed);
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
