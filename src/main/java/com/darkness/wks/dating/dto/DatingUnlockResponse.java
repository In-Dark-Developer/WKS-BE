package com.darkness.wks.dating.dto;

import java.util.Map;

/**
 * POST /api/dating/candidates/{candidateId}/unlock 응답. plan.md §8.5.
 * {@code values} 는 요청한 필드명({@code PHOTO}·{@code NAME}·...) → 값이다. 값은 필드에 따라
 * 이름·학과·궁합 까닭 문장 또는 원본 사진의 서명된 임시 URL이다.
 */
public record DatingUnlockResponse(Map<String, String> values, int balance) {
}
