package com.darkness.wks.dating.dto;

import com.darkness.wks.dating.DatingUnlockField;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * POST /api/dating/candidates/{candidateId}/unlock 요청. plan.md §8.5.
 * 사용자가 고른 필드를 한 번에 받는다(2026-09-27, 단건 {@code field} 에서 교체). 네 개를 전부 고르면
 * plan.md §1.4 의 "전체 해금 25"와 같다. 중복은 서비스가 한 번으로 친다.
 */
public record DatingUnlockRequest(@NotEmpty List<@NotNull DatingUnlockField> fields) {
}
