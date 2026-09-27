package com.darkness.wks.signup;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.signup.dto.CreateSignupRequest;
import com.darkness.wks.signup.dto.ResendSignupResponse;
import com.darkness.wks.signup.dto.SignupResponse;
import com.darkness.wks.signup.entity.EmailVerification;
import com.darkness.wks.signup.entity.Signup;
import com.darkness.wks.signup.entity.SignupReapplyInvite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignupService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[016789]-?\\d{3,4}-?\\d{4}$");

    private final SignupRepository signupRepository;
    private final ResultRepository resultRepository;
    private final EmailVerificationService emailVerificationService;
    private final PhotoUploadService photoUploadService;
    private final SignupReapplyService reapplyService;

    // 팀 결정 전 임시 설정값. 비워두면(로컬 기본값) 도메인 검증을 건너뛴다 — docs/todo.md §3 "학교 웹메일 도메인 화이트리스트" 미결정 참고
    @Value("${app.signup.allowed-email-domains}")
    private String allowedEmailDomainsRaw;

    @Transactional
    public SignupResponse createSignup(CreateSignupRequest request) {
        String email = request.email().trim();
        validateEmailDomain(email);
        if (signupRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SIGNUP);
        }
        String contactValue = blankToNull(request.contactValue());
        validateContact(request.contactMethod(), contactValue);
        String photoKey = blankToNull(request.photoKey());
        photoUploadService.verifyPhotoExists(photoKey);

        Result result = resolveResult(request.resultId());

        Signup signup = new Signup(email, result, request.gender(), request.preferGender(),
                blankToNull(request.name()), request.contactMethod(), contactValue,
                blankToNull(request.department()), normalizeMbti(request.mbti()), blankToNull(request.bio()), photoKey);
        signup.issueCoupon();
        signupRepository.save(signup);

        boolean mailSent = canLinkByInvite(signup)
                ? trySendInvite(signup)
                : trySendVerificationEmail(email, emailVerificationService.issueToken(signup).getToken());

        log.info("signup created. id={}, mailSent={}", signup.getId(), mailSent);
        return SignupResponse.of(signup, mailSent);
    }

    @Transactional
    public ResendSignupResponse resend(String email) {
        Signup signup = signupRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        // 매직링크 대상은 이메일 인증 여부와 무관하다 — 메일을 잃어버렸으면 새 초대를 다시 보낸다
        if (canLinkByInvite(signup)) {
            return ResendSignupResponse.of(trySendInvite(signup));
        }
        if (signup.isVerified()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        EmailVerification verification = emailVerificationService.issueToken(signup);
        boolean mailSent = trySendVerificationEmail(signup.getEmail(), verification.getToken());
        return ResendSignupResponse.of(mailSent);
    }

    /**
     * 사주 결과가 있고 아직 어느 계정에도 붙지 않았으면 인증 메일 대신 재신청 매직링크를 보낸다(2026-09-27).
     * 소개팅이 열린 뒤의 사전신청은 결국 카카오 로그인으로 그 결과를 계정에 잇고 소개팅 신청을 마쳐야 하는데,
     * 인증 메일로는 그 흐름에 들어갈 방법이 없다. 결과가 없으면 이을 것이 없어 기존 인증 메일을 보낸다.
     */
    private boolean canLinkByInvite(Signup signup) {
        return signup.getResult() != null && signup.getResult().getMemberId() == null;
    }

    private boolean trySendInvite(Signup signup) {
        SignupReapplyInvite invite = reapplyService.issueInvite(signup.getId());
        try {
            reapplyService.sendInviteEmail(signup.getEmail(), invite.getToken());
            return true;
        } catch (SignupReapplyService.InviteMailFailedException exception) {
            // 남겨두면 유효한 초대로 보여 캠페인 재시도 대상에서 빠진다(SignupReapplyCampaignRunner 와 같은 처리)
            reapplyService.discardInvite(invite.getToken());
            log.warn("signup invite mail failed. signupId={}, cause={}", signup.getId(),
                    exception.getCause().getClass().getSimpleName());
            return false;
        }
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerification verification = emailVerificationService.verify(token);
        verification.getSignup().markVerified(Instant.now());
    }

    private boolean trySendVerificationEmail(String email, String token) {
        try {
            emailVerificationService.sendVerificationEmail(email, token);
            return true;
        } catch (EmailVerificationService.MailSendFailedException exception) {
            return false;
        }
    }

    private Result resolveResult(String resultId) {
        if (resultId == null || resultId.isBlank()) {
            return null;
        }
        UUID id = parseResultId(resultId);
        return resultRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
    }

    private void validateEmailDomain(String email) {
        Set<String> allowedDomains = parseAllowedDomains();
        if (allowedDomains.isEmpty()) {
            // 화이트리스트 미설정 — 팀 결정 전까지 검증을 건너뛴다
            return;
        }
        int at = email.lastIndexOf('@');
        String domain = at >= 0 ? email.substring(at + 1).toLowerCase(Locale.ROOT) : "";
        boolean allowed = allowedDomains.stream().anyMatch(allowedDomain ->
                domain.equals(allowedDomain) || domain.endsWith("." + allowedDomain));
        if (!allowed) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_DOMAIN);
        }
    }

    private void validateContact(ContactMethod contactMethod, String contactValue) {
        if (contactMethod == ContactMethod.PHONE && contactValue != null
                && !PHONE_PATTERN.matcher(contactValue).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeMbti(String mbti) {
        String normalized = blankToNull(mbti);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private Set<String> parseAllowedDomains() {
        if (allowedEmailDomainsRaw == null || allowedEmailDomainsRaw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedEmailDomainsRaw.split(","))
                .map(String::trim)
                .filter(domain -> !domain.isEmpty())
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private UUID parseResultId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value) || id.version() != 4) {
                throw new IllegalArgumentException("Invalid UUID v4");
            }
            return id;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
