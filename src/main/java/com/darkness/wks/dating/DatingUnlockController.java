package com.darkness.wks.dating;

import com.darkness.wks.common.auth.CurrentMember;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.dating.dto.DatingUnlockRequest;
import com.darkness.wks.dating.dto.DatingUnlockResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Dating", description = "소개팅 프로필 및 후보 API. 로그인 쿠키(wks_token) 필요")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequestMapping("/api/dating/candidates")
public class DatingUnlockController {

    private final DatingUnlockService unlockService;

    public DatingUnlockController(DatingUnlockService unlockService) {
        this.unlockService = unlockService;
    }

    @Operation(summary = "카드 정보 해금", description = """
            사진·이름·학과·궁합 까닭 중 하나를 실로 해금한다 (plan.md §8.5). 이미 해금한 필드는 차감 없이
            값만 반환한다. 잔액이 모자라면 402 INSUFFICIENT_THREAD.
            """)
    @PostMapping("/{candidateId}/unlock")
    public ApiResponse<DatingUnlockResponse> unlock(@CurrentMember Long memberId,
            @PathVariable UUID candidateId, @Valid @RequestBody DatingUnlockRequest request) {
        return ApiResponse.success(unlockService.unlock(memberId, candidateId, request.field()));
    }
}
