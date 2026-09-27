package com.darkness.wks.auth;

import com.darkness.wks.auth.dto.KakaoLoginRequest;
import com.darkness.wks.auth.dto.KakaoLoginResponse;
import com.darkness.wks.common.auth.JwtCookie;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "카카오 로그인 API. 토큰은 HttpOnly 쿠키로 내려간다. 사주·궁합 등 나머지 API 는 인증이 없다.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtCookie jwtCookie;

    @Operation(summary = "카카오 로그인", description = """
            프론트가 카카오 인가 코드를 교환해 로그인시킨다. resultId 를 함께 보내면 계정에 저장된 결과가
            있는지에 따라 연결하거나 복원한다(plan.md §1.1). 인증이 필요 없는 API 다.
            발급된 토큰은 응답 바디가 아니라 Set-Cookie(HttpOnly, Secure, SameSite=Lax)로 내려간다 —
            프론트가 따로 저장·첨부할 필요 없이 브라우저가 이후 요청에 자동으로 실어 보낸다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = KakaoLoginResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "code 누락 또는 redirectUri 가 화이트리스트에 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "INVALID_INPUT",
                                        "message": "입력값이 올바르지 않습니다."
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "카카오 서버 오류·타임아웃, 또는 로그인 기능 미설정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "KAKAO_UNAVAILABLE",
                                        "message": "카카오 로그인을 일시적으로 사용할 수 없습니다."
                                      }
                                    }
                                    """)))
    })
    @PostMapping("/kakao")
    public ApiResponse<KakaoLoginResponse> loginWithKakao(@Valid @RequestBody KakaoLoginRequest request,
                                                           HttpServletResponse response) {
        AuthService.LoginOutcome outcome = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.issue(outcome.token()).toString());
        return ApiResponse.success(outcome.body());
    }

    @Operation(summary = "로그아웃", description = """
            로그인 쿠키를 지운다. 서버 쪽 토큰 무효화는 없다(jti 블록리스트 등 미구현) — 이미 브라우저
            밖으로 나간 토큰 사본은 만료(기본 15일)까지 그대로 유효하다. 인증 없이도 호출할 수 있다
            (쿠키가 없거나 이미 만료된 상태에서 호출해도 안전하게 아무 일도 안 한다).
            """)
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.clear().toString());
        return ApiResponse.success(null);
    }
}
