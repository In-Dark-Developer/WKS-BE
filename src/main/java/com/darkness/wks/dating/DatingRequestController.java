package com.darkness.wks.dating;

import com.darkness.wks.common.auth.CurrentMember;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.dating.dto.CreateDatingRequest;
import com.darkness.wks.dating.dto.DatingRequestResponse;
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

@Tag(name = "Dating Requests", description = "소개팅 요청·보관함. Bearer JWT 필요")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/dating/requests")
public class DatingRequestController {

    private final DatingRequestService requestService;

    public DatingRequestController(DatingRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DatingRequestResponse> send(@CurrentMember Long memberId,
            @Valid @RequestBody CreateDatingRequest request) {
        return ApiResponse.success(requestService.send(memberId, request.candidateId()));
    }

    @GetMapping
    public ApiResponse<List<DatingRequestResponse>> list(@CurrentMember Long memberId,
            @RequestParam String box) {
        return ApiResponse.success(requestService.list(memberId, box));
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<DatingRequestResponse> accept(@CurrentMember Long memberId, @PathVariable UUID id) {
        return ApiResponse.success(requestService.accept(memberId, id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<DatingRequestResponse> reject(@CurrentMember Long memberId, @PathVariable UUID id) {
        return ApiResponse.success(requestService.reject(memberId, id));
    }
}
