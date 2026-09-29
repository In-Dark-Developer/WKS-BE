package com.darkness.wks.admin;

import com.darkness.wks.admin.dto.AdminDatingProfileResponse;
import com.darkness.wks.admin.dto.AdminDatingProfileUpdateRequest;
import com.darkness.wks.admin.dto.AdminPhotoChangeRequest;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.dating.DatingAdminService;
import com.darkness.wks.dating.dto.DatingPhotoUploadRequest;
import com.darkness.wks.dating.dto.DatingPhotoUploadResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 소개팅 프로필 운영자 API. 프론트 계약이 아니라 Swagger 에서 숨긴다 — 사용법은 docs/admin-api.md 와 /admin.html.
 * 인증은 {@link AdminAuthInterceptor}.
 */
@Hidden
@Validated
@RestController
@RequestMapping("/api/admin/dating/profiles")
public class AdminDatingController {

    private final DatingAdminService adminService;

    public AdminDatingController(DatingAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ApiResponse<AdminDatingProfileResponse> findByEmail(@RequestParam @NotBlank String email) {
        return ApiResponse.success(adminService.findByEmail(email));
    }

    @GetMapping("/{profileId}")
    public ApiResponse<AdminDatingProfileResponse> get(@PathVariable UUID profileId) {
        return ApiResponse.success(adminService.get(profileId));
    }

    @PatchMapping("/{profileId}")
    public ApiResponse<AdminDatingProfileResponse> update(@PathVariable UUID profileId,
            @Valid @RequestBody AdminDatingProfileUpdateRequest request) {
        return ApiResponse.success(adminService.update(profileId, request));
    }

    @PostMapping("/{profileId}/deactivate")
    public ApiResponse<AdminDatingProfileResponse> deactivate(@PathVariable UUID profileId) {
        return ApiResponse.success(adminService.deactivate(profileId));
    }

    @PostMapping("/{profileId}/activate")
    public ApiResponse<AdminDatingProfileResponse> activate(@PathVariable UUID profileId) {
        return ApiResponse.success(adminService.activate(profileId));
    }

    @PostMapping("/{profileId}/photo-upload-url")
    public ApiResponse<DatingPhotoUploadResponse> createPhotoUploadUrl(@PathVariable UUID profileId,
            @Valid @RequestBody DatingPhotoUploadRequest request) {
        return ApiResponse.success(adminService.createPhotoUploadUrl(profileId, request.contentType()));
    }

    @PatchMapping("/{profileId}/photo")
    public ApiResponse<AdminDatingProfileResponse> changePhoto(@PathVariable UUID profileId,
            @Valid @RequestBody AdminPhotoChangeRequest request) {
        return ApiResponse.success(adminService.changePhoto(profileId, request.photoId()));
    }

    @DeleteMapping("/{profileId}")
    public ApiResponse<Void> delete(@PathVariable UUID profileId) {
        adminService.delete(profileId);
        return ApiResponse.success(null);
    }
}
