package com.darkness.wks.dating;

import com.darkness.wks.common.auth.CurrentMember;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.common.response.ErrorResponse;
import com.darkness.wks.dating.dto.CreateDatingRequest;
import com.darkness.wks.dating.dto.DatingRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Dating Requests", description = "소개팅 요청·보관함. 로그인 쿠키(wks_token) 필요")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequestMapping("/api/dating/requests")
public class DatingRequestController {

    private final DatingRequestService requestService;

    public DatingRequestController(DatingRequestService requestService) {
        this.requestService = requestService;
    }

    @Operation(summary = "소개팅 요청 보내기", description = """
            **현재 내 추천 카드(Top 3)에 있는 상대에게만** 보낼 수 있다. 양쪽 모두 학교메일 인증이 끝나 있어야
            하고, 두 사람 사이에 이미 요청이 있으면(방향 무관) 보낼 수 없다. 실 차감은 없다.
            상대 연락처는 수락 전까지 응답에 없다(contactValue가 null).
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "요청 생성"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_INPUT — 본인에게 요청, 또는 candidateId 누락·형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — 로그인 쿠키 없음·만료·위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "DATING_NOT_VERIFIED — 내 학교 이메일 인증 전",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DATING_PROFILE_NOT_FOUND — 내 프로필 없음, 또는 candidateId 로 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DATING_REQUEST_CONFLICT — 이미 요청이 있음, 상대가 내 현재 카드에 없음, 상대 미인증",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DatingRequestResponse> send(@CurrentMember Long memberId,
            @Valid @RequestBody CreateDatingRequest request) {
        return ApiResponse.success(requestService.send(memberId, request.candidateId()));
    }

    @Operation(summary = "요청 보관함", description = """
            box=sent 면 내가 보낸 요청, box=received 면 내가 받은 요청. 수락(ACCEPTED)된 건에만 상대
            연락처(contactValue)가 채워지고, PENDING·REJECTED 는 null 이다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_INPUT — box 가 sent·received 가 아님 또는 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — 로그인 쿠키 없음·만료·위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DATING_PROFILE_NOT_FOUND — 내 프로필 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ApiResponse<List<DatingRequestResponse>> list(@CurrentMember Long memberId,
            @Parameter(description = "sent | received", example = "received") @RequestParam String box) {
        return ApiResponse.success(requestService.list(memberId, box));
    }

    @Operation(summary = "요청 수락", description = """
            **받은 사람만** 수락할 수 있다. 수락하면 양쪽 응답에 서로의 연락처가 채워진다. 매칭이 성사돼도
            두 사람은 계속 다른 사람의 추천 후보로 남는다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수락 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — 로그인 쿠키 없음·만료·위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DATING_REQUEST_NOT_FOUND — 요청 없음, 또는 받은 사람 본인이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DATING_REQUEST_CONFLICT — 이미 처리된 요청, 또는 어느 한쪽 프로필이 인증 해제됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/accept")
    public ApiResponse<DatingRequestResponse> accept(@CurrentMember Long memberId, @PathVariable UUID id) {
        return ApiResponse.success(requestService.accept(memberId, id));
    }

    @Operation(summary = "요청 거절", description = "**받은 사람만** 거절할 수 있다. 거절해도 연락처는 양쪽 모두 공개되지 않는다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "거절 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — 로그인 쿠키 없음·만료·위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DATING_REQUEST_NOT_FOUND — 요청 없음, 또는 받은 사람 본인이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DATING_REQUEST_CONFLICT — 이미 처리된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/reject")
    public ApiResponse<DatingRequestResponse> reject(@CurrentMember Long memberId, @PathVariable UUID id) {
        return ApiResponse.success(requestService.reject(memberId, id));
    }
}
