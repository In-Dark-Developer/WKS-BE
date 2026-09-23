package com.darkness.wks.member;

import com.darkness.wks.common.auth.CurrentMember;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.common.response.ErrorResponse;
import com.darkness.wks.member.dto.MeResponse;
import com.darkness.wks.result.dto.ResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me", description = "로그인 회원 정보 API. Authorization: Bearer <token> 필요")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @Operation(summary = "내 정보", description = "로그인 상태 확인. 결과·소개팅 프로필 보유 여부와 실 잔액.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "UNAUTHENTICATED",
                                        "message": "로그인이 필요합니다."
                                      }
                                    }
                                    """)))
    })
    @GetMapping
    public ApiResponse<MeResponse> me(@CurrentMember Long memberId) {
        return ApiResponse.success(meService.getMe(memberId));
    }

    @Operation(summary = "내 결과 조회", description = """
            계정에 연결된 결과를 resultId 없이 토큰만으로 돌려준다. GET /api/results/{resultId} 와 같은
            구조다. 브라우저가 resultId 를 잃어도 로그인 상태면 복원할 수 있다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "계정에 연결된 결과 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "RESULT_NOT_FOUND",
                                        "message": "사주 결과를 찾을 수 없습니다."
                                      }
                                    }
                                    """)))
    })
    @GetMapping("/result")
    public ApiResponse<ResultResponse> myResult(@CurrentMember Long memberId) {
        return ApiResponse.success(meService.getMyResult(memberId));
    }
}
