package com.darkness.wks.dating;

import com.darkness.wks.admin.dto.AdminDatingProfileResponse;
import com.darkness.wks.admin.dto.AdminDatingProfileUpdateRequest;
import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.dto.DatingPhotoUploadResponse;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 운영자용 소개팅 프로필 처리 (#147). 탈퇴·삭제 요청은 운영자가 직접 처리한다는 결정(plan.md §12)을
 * SQL 대신 이 서비스로 한다. 검증 규칙은 {@link DatingProfileService} 것을 그대로 쓴다.
 * 로그에는 profileId·memberId 만 남긴다 — 이메일·수정 값은 안 남긴다.
 */
@Slf4j
@Service
public class DatingAdminService {

    private final DatingProfileRepository profileRepository;
    private final DatingPhotoRepository photoRepository;
    private final DatingPhotoService photoService;
    private final DatingProfileService profileService;

    public DatingAdminService(DatingProfileRepository profileRepository, DatingPhotoRepository photoRepository,
                              DatingPhotoService photoService, DatingProfileService profileService) {
        this.profileRepository = profileRepository;
        this.photoRepository = photoRepository;
        this.photoService = photoService;
        this.profileService = profileService;
    }

    @Transactional(readOnly = true)
    public AdminDatingProfileResponse findByEmail(String rawEmail) {
        return response(profileRepository.findWithPhotoByEmail(DatingProfileService.normalize(rawEmail))
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public AdminDatingProfileResponse get(UUID profileId) {
        return response(load(profileId));
    }

    @Transactional
    public AdminDatingProfileResponse update(UUID profileId, AdminDatingProfileUpdateRequest request) {
        DatingProfile profile = load(profileId);
        String email = profile.getEmail();
        if (request.email() != null) {
            email = DatingProfileService.normalize(request.email());
            // 운영자 수정은 학교메일 코드 인증을 다시 거치지 않는다. 도메인·중복 검사만 같이 한다
            profileService.validateEmail(profile.getMemberId(), email);
        }
        ContactMethod contactMethod = request.contactMethod() != null ? request.contactMethod()
                : profile.getContactMethod();
        String contactValue = request.contactValue() != null ? request.contactValue().trim()
                : profile.getContactValue();
        DatingProfileService.validateContact(contactMethod, contactValue);
        profile.edit(email,
                request.name() != null ? request.name().trim() : profile.getName(),
                contactMethod, contactValue,
                request.department() != null ? request.department().trim() : profile.getDepartment(),
                request.mbti() != null ? request.mbti() : profile.getMbti(),
                request.bio() != null ? request.bio().trim() : profile.getBio());
        try {
            profileRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DATING_PROFILE_CONFLICT);
        }
        log.info("admin edited dating profile. profileId={}, memberId={}", profile.getId(), profile.getMemberId());
        return response(profile);
    }

    @Transactional
    public AdminDatingProfileResponse deactivate(UUID profileId) {
        DatingProfile profile = load(profileId);
        profile.deactivate(Instant.now());
        log.info("admin deactivated dating profile. profileId={}", profileId);
        return response(profile);
    }

    @Transactional
    public AdminDatingProfileResponse activate(UUID profileId) {
        DatingProfile profile = load(profileId);
        profile.activate();
        log.info("admin activated dating profile. profileId={}", profileId);
        return response(profile);
    }

    /**
     * readOnly 트랜잭션으로 감싸면 안 된다 — 안에서 사진 행을 INSERT 하는데 readOnly 는 flush 를 버려
     * photoId 만 응답에 나가고 행은 남지 않는다(2026-09-30 운영에서 교체가 전부 INVALID_INPUT 으로 실패한 원인).
     */
    public DatingPhotoUploadResponse createPhotoUploadUrl(UUID profileId, String contentType) {
        // 사진 행은 그 회원 소유로 만든다 — 교체 시 verifyOwnedPhoto 가 같은 규칙으로 검사한다
        return photoService.createUploadUrl(load(profileId).getMemberId(), contentType);
    }

    /**
     * S3 확인·블러 생성·삭제가 있어 트랜잭션으로 묶지 않는다(DatingProfileService.create 와 같은 이유).
     * 순서: 새 사진 검사·썸네일 → 프로필에 붙이기 → 옛 행 삭제 → 옛 S3 객체 삭제. 중간에 실패하면
     * 옛 행·객체가 남을 뿐 프로필은 항상 유효한 사진을 가리킨다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminDatingProfileResponse changePhoto(UUID profileId, UUID photoId) {
        DatingProfile profile = load(profileId);
        DatingPhoto old = profile.getPhoto();
        if (old.getId().equals(photoId)) {
            return response(profile);
        }
        DatingPhoto fresh = photoService.verifyOwnedPhoto(profile.getMemberId(), photoId);
        photoService.createBlurredThumbnail(fresh);
        profile.changePhoto(fresh);
        profileRepository.saveAndFlush(profile);
        photoRepository.delete(old);
        photoService.deleteObjects(old);
        log.info("admin changed dating photo. profileId={}, oldPhotoId={}, newPhotoId={}",
                profileId, old.getId(), fresh.getId());
        return response(profile);
    }

    /**
     * 프로필 hard delete. 추천(후보 측)·요청·메일 인증 행은 FK cascade 로 함께 지워지고 실 원장은 남는다.
     * 사진은 프로필→사진 방향 FK 라 cascade 가 없어 뒤에 따로 지운다. 같은 회원은 다시 등록할 수 있다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void delete(UUID profileId) {
        DatingProfile profile = load(profileId);
        DatingPhoto photo = profile.getPhoto();
        profileRepository.delete(profile);
        photoRepository.delete(photo);
        photoService.deleteObjects(photo);
        log.info("admin deleted dating profile. profileId={}, memberId={}", profileId, profile.getMemberId());
    }

    private DatingProfile load(UUID profileId) {
        return profileRepository.findWithPhotoById(profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND));
    }

    private AdminDatingProfileResponse response(DatingProfile profile) {
        return AdminDatingProfileResponse.from(profile, photoService.originalUrl(profile.getPhoto()));
    }
}
