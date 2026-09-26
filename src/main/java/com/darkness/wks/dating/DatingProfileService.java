package com.darkness.wks.dating;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
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
    private final SignupReapplyService reapplyService;

    @Value("${app.signup.allowed-email-domains:}")
    private String allowedDomainsRaw;

    public DatingProfileService(DatingProfileRepository profileRepository, ResultRepository resultRepository,
                                DatingPhotoService photoService, DatingEmailVerificationService emailVerificationService,
                                SignupReapplyService reapplyService) {
        this.profileRepository = profileRepository;
        this.resultRepository = resultRepository;
        this.photoService = photoService;
        this.emailVerificationService = emailVerificationService;
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
        if (invited) {
            // 초대 메일을 받은 사람만 이 토큰을 가질 수 있으므로 학교메일 소유는 이미 증명됐다 — 인증 메일을
            // 한 번 더 보내지 않는다. 등록이 롤백되면 초대도 같이 살아난다(같은 트랜잭션)
            saved.markVerified(Instant.now());
            reapplyService.consumeInvite(request.reapplyToken());
        } else {
            issueAndSendVerification(saved);
        }
        return DatingProfileResponse.from(saved);
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

    private void issueAndSendVerification(DatingProfile profile) {
        DatingEmailVerification verification = emailVerificationService.issueToken(profile);
        try {
            emailVerificationService.sendVerificationEmail(profile.getEmail(), verification.getToken());
        } catch (DatingEmailVerificationService.MailSendFailedException exception) {
            log.warn("dating profile verification mail send failed. profileId={}", profile.getId());
        }
    }

    public DatingProfileResponse getMine(Long memberId) {
        return DatingProfileResponse.from(profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND)));
    }

    private void validate(Long memberId, DatingProfileRequest request) {
        String email = normalize(request.email());
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
        if (request.contactMethod() == ContactMethod.PHONE
                && !PHONE_PATTERN.matcher(request.contactValue()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
