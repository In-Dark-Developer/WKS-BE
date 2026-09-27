package com.darkness.wks.signup;

import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.common.response.ErrorResponse;
import com.darkness.wks.signup.dto.CreateSignupRequest;
import com.darkness.wks.signup.dto.PhotoUploadUrlRequest;
import com.darkness.wks.signup.dto.PhotoUploadUrlResponse;
import com.darkness.wks.signup.dto.ResendSignupRequest;
import com.darkness.wks.signup.dto.ResendSignupResponse;
import com.darkness.wks.signup.dto.SignupReapplyResponse;
import com.darkness.wks.signup.dto.SignupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@Tag(name = "Signup", description = "소개팅 사전등록 API")
@RestController
@RequestMapping("/api/signups")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;
    private final PhotoUploadService photoUploadService;
    private final SignupReapplyService reapplyService;

    @Value("${app.frontend.verify-redirect-url}")
    private String verifyRedirectUrl;

    @Operation(summary = "사진 업로드 URL 발급", description = "S3에 직접 업로드할 presigned URL을 발급한다. 응답의 photoKey를 이후 POST /api/signups 요청에 그대로 담아 보낸다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "허용되지 않는 contentType",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "INVALID_INPUT",
                                        "message": "입력값이 올바르지 않습니다."
                                      }
                                    }
                                    """)))
    })
    @PostMapping("/photo-upload-url")
    public ApiResponse<PhotoUploadUrlResponse> createPhotoUploadUrl(@Valid @RequestBody PhotoUploadUrlRequest request) {
        return ApiResponse.success(photoUploadService.createUploadUrl(request.contentType()));
    }

    @Operation(summary = "사전등록 신청", description = "이름·이메일·연락처·학과·MBTI·자기소개·성별·선호성별·사진으로 신청하고 인증 메일을 발송한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "신청 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류 또는 허용되지 않는 이메일 도메인",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "INVALID_EMAIL_DOMAIN",
                                        "message": "허용되지 않는 이메일 도메인입니다."
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "resultId 로 조회되는 사주 결과 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "RESULT_NOT_FOUND",
                                        "message": "사주 결과를 찾을 수 없습니다."
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 신청한 이메일",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "DUPLICATE_SIGNUP",
                                        "message": "이미 신청된 이메일입니다."
                                      }
                                    }
                                    """)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> createSignup(@Valid @RequestBody CreateSignupRequest request) {
        return ApiResponse.success(signupService.createSignup(request));
    }

    @Operation(summary = "인증 메일 재발송", description = "이미 인증 완료된 이메일이면 400을 반환한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발송 처리"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "신청 내역 없음 또는 이미 인증 완료",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "INVALID_INPUT",
                                        "message": "입력값이 올바르지 않습니다."
                                      }
                                    }
                                    """)))
    })
    @PostMapping("/resend")
    public ApiResponse<ResendSignupResponse> resend(@Valid @RequestBody ResendSignupRequest request) {
        return ApiResponse.success(signupService.resend(request.email()));
    }

    @Operation(summary = "이메일 인증", description = "매직링크 클릭. 성공 시 프론트 완료 페이지로 302 리다이렉트한다.")
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
    @GetMapping("/verify")
    public ResponseEntity<Void> verify(
            @Parameter(description = "이메일로 발송된 인증 토큰") @RequestParam String token
    ) {
        signupService.verifyEmail(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(verifyRedirectUrl))
                .build();
    }

    @Operation(summary = "재신청 폼 자동 채움", description = """
            기존 사전신청자에게 보낸 재신청 초대 링크의 토큰으로 당시 입력값을 돌려준다. 로그인 불필요이고
            토큰은 여기서 소비되지 않는다 — 소개팅 프로필 등록(POST /api/dating/profile)까지 유효하다.
            사전신청 때 선택값이라 비어 있던 항목은 null 이므로 화면에서 받아야 한다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "만료·위조·이미 완료된 토큰",
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
    @GetMapping("/reapply")
    public ApiResponse<SignupReapplyResponse> reapply(
            @Parameter(description = "재신청 초대 메일의 토큰") @RequestParam String token
    ) {
        return ApiResponse.success(reapplyService.prefill(token));
    }
}
