package com.darkness.wks.dating;

import com.darkness.wks.common.auth.CurrentMember;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.dating.dto.DatingPhotoUploadRequest;
import com.darkness.wks.dating.dto.DatingPhotoUploadResponse;
import com.darkness.wks.dating.dto.DatingProfileRequest;
import com.darkness.wks.dating.dto.DatingProfileResponse;
import com.darkness.wks.dating.dto.DatingRecommendationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dating", description = "소개팅 프로필 및 후보 API. Bearer JWT 필요")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/dating")
public class DatingController {

    private static final String PHONE_PROFILE_EXAMPLE = """
            {"email":"student@dgu.ac.kr","name":"홍길동","contactMethod":"PHONE",
             "contactValue":"010-3333-3333","department":"컴퓨터공학과","mbti":"ESTP",
             "bio":"안녕하세요","photoId":"3fa85f64-5717-4562-b3fc-2c963f66afa6"}
            """;
    private static final String INSTAGRAM_PROFILE_EXAMPLE = """
            {"email":"student@dgu.ac.kr","name":"홍길동","contactMethod":"INSTAGRAM",
             "contactValue":"my_insta_id","department":"컴퓨터공학과","mbti":"ESTP",
             "bio":"안녕하세요","photoId":"3fa85f64-5717-4562-b3fc-2c963f66afa6"}
            """;

    private final DatingPhotoService photoService;
    private final DatingProfileService profileService;
    private final DatingRecommendationService recommendationService;

    public DatingController(DatingPhotoService photoService, DatingProfileService profileService,
                            DatingRecommendationService recommendationService) {
        this.photoService = photoService;
        this.profileService = profileService;
        this.recommendationService = recommendationService;
    }

    @PostMapping("/profile/photo")
    public ApiResponse<DatingPhotoUploadResponse> photo(@CurrentMember Long memberId,
            @Valid @RequestBody DatingPhotoUploadRequest request) {
        return ApiResponse.success(photoService.createUploadUrl(memberId, request.contentType()));
    }

    @PostMapping("/profile")
    @Operation(requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "photoId는 사진 업로드 URL 발급 응답에서 받은 실제 값으로 교체",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DatingProfileRequest.class),
                    examples = {
                            @ExampleObject(name = "전화번호", value = PHONE_PROFILE_EXAMPLE),
                            @ExampleObject(name = "인스타그램", value = INSTAGRAM_PROFILE_EXAMPLE)
                    })))
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DatingProfileResponse> createProfile(@CurrentMember Long memberId,
            @Valid @RequestBody DatingProfileRequest request) {
        return ApiResponse.success(profileService.create(memberId, request));
    }

    @GetMapping("/profile/me")
    public ApiResponse<DatingProfileResponse> myProfile(@CurrentMember Long memberId) {
        return ApiResponse.success(profileService.getMine(memberId));
    }

    @GetMapping("/recommendations")
    public ApiResponse<DatingRecommendationResponse> recommendations(@CurrentMember Long memberId) {
        return ApiResponse.success(recommendationService.getCurrent(memberId));
    }
}
