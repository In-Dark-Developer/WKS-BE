package com.darkness.wks.dating.dto;

/**
 * POST /api/dating/candidates/{candidateId}/unlock 응답. plan.md §8.5.
 * {@code value} 는 필드에 따라 이름·학과·궁합 까닭 문장 또는 원본 사진의 서명된 임시 URL이다.
 */
public record DatingUnlockResponse(String field, String value, int balance) {
}
