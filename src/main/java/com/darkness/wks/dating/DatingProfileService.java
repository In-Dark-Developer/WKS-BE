package com.darkness.wks.dating;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.dto.DatingProfileRequest;
import com.darkness.wks.dating.dto.DatingProfileResponse;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.result.ResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DatingProfileService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[016789]-?\\d{3,4}-?\\d{4}$");

    private final DatingProfileRepository profileRepository;
    private final ResultRepository resultRepository;
    private final DatingPhotoService photoService;

    @Value("${app.signup.allowed-email-domains:}")
    private String allowedDomainsRaw;

    public DatingProfileService(DatingProfileRepository profileRepository, ResultRepository resultRepository,
                                DatingPhotoService photoService) {
        this.profileRepository = profileRepository;
        this.resultRepository = resultRepository;
        this.photoService = photoService;
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
        DatingPhoto photo = photoService.verifyOwnedPhoto(memberId, request.photoId());
        DatingProfile profile = new DatingProfile(memberId, normalize(request.email()),
                request.name().trim(), request.contactMethod(), request.contactValue().trim(),
                request.department().trim(), request.mbti(), request.bio().trim(), photo);
        try {
            return DatingProfileResponse.from(profileRepository.saveAndFlush(profile));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DATING_PROFILE_CONFLICT);
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
