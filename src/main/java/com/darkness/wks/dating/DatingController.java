package com.darkness.wks.dating;

import com.darkness.wks.common.auth.CurrentMember;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.common.response.ErrorResponse;
import com.darkness.wks.dating.dto.DatingPhotoUploadRequest;
import com.darkness.wks.dating.dto.DatingPhotoUploadResponse;
import com.darkness.wks.dating.dto.DatingProfileRequest;
import com.darkness.wks.dating.dto.DatingProfileResponse;
import com.darkness.wks.dating.dto.DatingRecommendationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Dating", description = "소개팅 프로필 및 후보 API. 로그인 쿠키(wks_token) 필요")
@SecurityRequirement(name = "cookieAuth")
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

    @Value("${app.frontend.dating-verify-redirect-url}")
    private String verifyRedirectUrl;

    public DatingController(DatingPhotoService photoService, DatingProfileService profileService,
                            DatingRecommendationService recommendationService) {
        this.photoService = photoService;
        this.profileService = profileService;
        this.recommendationService = recommendationService;
    }

    @Operation(summary = "사진 업로드 URL 발급", description = """
            S3에 직접 업로드할 presigned URL과 photoId를 발급한다. **이 API는 파일을 받지 않는다** —
            발급된 uploadUrl에 같은 Content-Type으로 PUT한 뒤 photoId를 프로필 등록 요청에 넣는다.
            허용 형식은 image/jpeg·image/png 뿐이다(그 외 INVALID_INPUT).
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_INPUT — 허용되지 않는 contentType",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — 로그인 쿠키 없음·만료·위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/profile/photo")
    public ApiResponse<DatingPhotoUploadResponse> photo(@CurrentMember Long memberId,
            @Valid @RequestBody DatingPhotoUploadRequest request) {
        return ApiResponse.success(photoService.createUploadUrl(memberId, request.contentType()));
    }

    @PostMapping("/profile")
    @Operation(summary = "소개팅 프로필 등록", description = """
            계정당 한 번만 등록된다. 모든 필드가 필수이고, photoId는 **본인에게 발급됐고 S3 업로드가 끝난**
            사진이어야 한다. 등록 성공 시 학교메일 인증 메일이 자동 발송되며, 인증 전에는 emailVerified가
            false이고 후보 조회가 막힌다. 프로필 수정·사진 교체 API는 V1에 없다.
            reapplyToken(선택)은 기존 사전신청자 재신청 전용 — 넣으면 인증 메일 없이 즉시 인증 완료 처리된다.
            """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "photoId는 사진 업로드 URL 발급 응답에서 받은 실제 값으로 교체",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DatingProfileRequest.class),
                    examples = {
                            @ExampleObject(name = "전화번호", value = PHONE_PROFILE_EXAMPLE),
                            @ExampleObject(name = "인스타그램", value = INSTAGRAM_PROFILE_EXAMPLE)
                    })))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = """
                    INVALID_EMAIL_DOMAIN — 학교 메일 아님 / INVALID_INPUT — 입력값·전화번호 형식 오류, \
                    재신청 초대와 다른 이메일 / INVALID_TOKEN — 만료·위조·이미 쓴 reapplyToken""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — 로그인 쿠키 없음·만료·위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "RESULT_NOT_FOUND — 계정에 연결된 사주 결과 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DATING_PROFILE_CONFLICT — 이미 등록한 계정이거나 이미 쓰인 학교 이메일",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DatingProfileResponse> createProfile(@CurrentMember Long memberId,
            @Valid @RequestBody DatingProfileRequest request) {
        return ApiResponse.success(profileService.create(memberId, request));
    }

    @Operation(summary = "내 소개팅 프로필 조회", description = """
            응답 구조는 프로필 등록과 같다. 프로필 수정·사진 교체는 V1에 없어서 이 경로의 PATCH는
            METHOD_NOT_ALLOWED 405다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — 로그인 쿠키 없음·만료·위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DATING_PROFILE_NOT_FOUND — 내 프로필 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/profile/me")
    public ApiResponse<DatingProfileResponse> myProfile(@CurrentMember Long memberId) {
        return ApiResponse.success(profileService.getMine(memberId));
    }

    @Operation(summary = "학교 이메일 인증", description = "매직링크 클릭. 성공 시 프론트 완료 페이지로 302 리다이렉트한다. 로그인 쿠키 불필요")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "인증 성공, 프론트로 리다이렉트"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "만료·위조·재사용 토큰",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "INVALID_TOKEN",
                                        "message": "유효하지 않거나 만료된 토큰입니다."
                                      }
                                    }
                                    """)))
    })
    @GetMapping("/profile/verify")
    public ResponseEntity<Void> verifyEmail(
            @Parameter(description = "이메일로 발송된 인증 토큰") @RequestParam String token
    ) {
        profileService.verifyEmail(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(verifyRedirectUrl))
                .build();
    }

    @Operation(summary = "현재 후보 (Top 3)", description = """
            학교메일 인증을 마친 이성 신청자 중 궁합 점수 내림차순 최대 3명. 한 번 카드에 나온 후보는
            리롤 이후 새 추천에서 제외된다. 카드가 3장인 동안에는 새 신청자가 와도 재조회로 교체되지 않는다.
            fields.*는 잠겨 있으면 cost만, 해금됐으면 value만 채운다(잠긴 값은 응답에 넣지 않는다).
            후보가 없으면 candidates는 빈 배열이다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — 로그인 쿠키 없음·만료·위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "DATING_NOT_VERIFIED — 학교 이메일 인증 전",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DATING_PROFILE_NOT_FOUND — 내 프로필 없음 / RESULT_NOT_FOUND — 계정에 연결된 사주 결과 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/recommendations")
    public ApiResponse<DatingRecommendationResponse> recommendations(@CurrentMember Long memberId) {
        return ApiResponse.success(recommendationService.getCurrent(memberId));
    }
}
