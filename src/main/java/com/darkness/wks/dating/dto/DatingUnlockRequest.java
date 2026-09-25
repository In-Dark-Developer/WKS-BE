package com.darkness.wks.dating.dto;

import com.darkness.wks.dating.DatingUnlockField;
import jakarta.validation.constraints.NotNull;

/** POST /api/dating/candidates/{candidateId}/unlock 요청. plan.md §8.5. */
public record DatingUnlockRequest(@NotNull DatingUnlockField field) {
}
