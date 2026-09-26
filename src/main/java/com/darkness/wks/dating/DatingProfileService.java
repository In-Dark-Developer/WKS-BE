package com.darkness.wks.dating;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.dto.DatingEmailCodeResponse;
import com.darkness.wks.dating.dto.DatingEmailCodeVerifyResponse;
import com.darkness.wks.dating.dto.DatingProfileRequest;
import com.darkness.wks.dating.dto.DatingProfileResponse;
import com.darkness.wks.dating.entity.DatingEmailVerification;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.signup.SignupReapplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class DatingProfileService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[016789]-?\\d{3,4}-?\\d{4}$");

    private final DatingProfileRepository profileRepository;
    private final ResultRepository resultRepository;
    private final DatingPhotoService photoService;
    private final DatingEmailVerificationService emailVerificationService;
    private final DatingEmailCodeService emailCodeService;
    private final SignupReapplyService reapplyService;

    @Value("${app.signup.allowed-email-domains:}")
    private String allowedDomainsRaw;

    public DatingProfileService(DatingProfileRepository profileRepository, ResultRepository resultRepository,
                                DatingPhotoService photoService, DatingEmailVerificationService emailVerificationService,
                                DatingEmailCodeService emailCodeService, SignupReapplyService reapplyService) {
        this.profileRepository = profileRepository;
        this.resultRepository = resultRepository;
        this.photoService = photoService;
        this.emailVerificationService = emailVerificationService;
        this.emailCodeService = emailCodeService;
        this.reapplyService = reapplyService;
    }

    @Transactional
    public DatingProfileResponse create(Long memberId, DatingProfileRequest request) {
        if (profileRepository.existsByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.DATING_PROFILE_CONFLICT);
        }
        validate(memberId, request);
        if (!resultRepository.existsByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.RESULT_NOT_FOUND);
        }
        boolean invited = hasUsableReapplyInvite(request);
        if (!invited && !emailCodeService.isVerified(memberId, normalize(request.email()))) {
            // 학교메일 인증은 등록 전에 코드로 끝낸다(V23). 인증 안 된 프로필은 더 이상 만들지 않는다
            throw new BusinessException(ErrorCode.DATING_NOT_VERIFIED);
        }
        DatingPhoto photo = photoService.verifyOwnedPhoto(memberId, request.photoId());
        photoService.createBlurredThumbnail(photo);
        DatingProfile profile = new DatingProfile(memberId, normalize(request.email()),
                request.name().trim(), request.contactMethod(), request.contactValue().trim(),
                request.department().trim(), request.mbti(), request.bio().trim(), photo);
        DatingProfile saved;
        try {
            saved = profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DATING_PROFILE_CONFLICT);
        }
        saved.markVerified(Instant.now());
        if (invited) {
            // 초대 메일을 받은 사람만 이 토큰을 가질 수 있으므로 학교메일 소유는 이미 증명됐다.
            // 등록이 롤백되면 초대도 같이 살아난다(같은 트랜잭션)
            reapplyService.consumeInvite(request.reapplyToken());
        }
        return DatingProfileResponse.from(saved);
    }

    /**
     * 인증 코드 발송. 코드를 받고 나서야 막히면 메일만 낭비되므로 프로필 등록과 같은 이메일 검사를 먼저 한다.
     * SMTP 호출을 트랜잭션 밖에 두려고 이 메서드 자체는 트랜잭션을 열지 않는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DatingEmailCodeResponse sendEmailCode(Long memberId, String rawEmail) {
        String email = normalize(rawEmail);
        if (profileRepository.existsByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.DATING_PROFILE_CONFLICT);
        }
        validateEmail(memberId, email);
        DatingEmailCodeService.IssuedCode issued = emailCodeService.issue(memberId, email);
        try {
            emailCodeService.sendCodeEmail(email, issued.code());
        } catch (DatingEmailCodeService.MailSendFailedException exception) {
            emailCodeService.discard(memberId);
            // 이메일·코드는 로그에 남기지 않는다 (AGENTS.md). 원인은 예외 타입까지만
            log.warn("dating email code mail failed. memberId={}, cause={}", memberId,
                    exception.getCause().getClass().getSimpleName());
            throw new BusinessException(ErrorCode.MAIL_UNAVAILABLE);
        }
        return new DatingEmailCodeResponse(issued.expiresAt(), issued.resendAvailableAt());
    }

    // 바깥 트랜잭션에 합류하면 틀린 입력의 실패 횟수가 예외와 함께 롤백된다 — 코드 서비스가 자기 트랜잭션을 열게 둔다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DatingEmailCodeVerifyResponse verifyEmailCode(Long memberId, String rawEmail, String code) {
        String email = normalize(rawEmail);
        emailCodeService.verify(memberId, email, code);
        return new DatingEmailCodeVerifyResponse(email, true);
    }

    @Transactional
    public void verifyEmail(String token) {
        DatingEmailVerification verification = emailVerificationService.verify(token);
        verification.getProfile().markVerified(Instant.now());
    }

    private boolean hasUsableReapplyInvite(DatingProfileRequest request) {
        String token = request.reapplyToken();
        if (token == null || token.isBlank()) {
            return false;
        }
        if (!reapplyService.invitedEmail(token).equals(normalize(request.email()))) {
            // 초대는 그 주소의 소유 증명일 뿐이다 — 다른 주소의 인증으로 넘겨 쓸 수 없다
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return true;
    }

    public DatingProfileResponse getMine(Long memberId) {
        return DatingProfileResponse.from(profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND)));
    }

    private void validate(Long memberId, DatingProfileRequest request) {
        validateEmail(memberId, normalize(request.email()));
        if (request.contactMethod() == ContactMethod.PHONE
                && !PHONE_PATTERN.matcher(request.contactValue()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateEmail(Long memberId, String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1);
        Set<String> allowed = Arrays.stream(allowedDomainsRaw.split(","))
                .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty()).collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            allowed = Set.of("dgu.ac.kr");
        }
        if (allowed.stream().noneMatch(value -> domain.equals(value) || domain.endsWith("." + value))) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_DOMAIN);
        }
        if (profileRepository.existsByEmailAndMemberIdNot(email, memberId)) {
            throw new BusinessException(ErrorCode.DATING_PROFILE_CONFLICT);
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
