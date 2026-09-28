package com.darkness.wks.saju;

/**
 * Gemini 호출을 어느 화면이 쓰는지. 유료 대체 예산을 파트별로 나눠 한 파트가 유료 한도를 다 쓰지 못하게 한다.
 * 호출 1회 비용이 파트마다 달라서(사주 해석은 긴 글 5개) 같은 금액도 파트별 허용 횟수가 다르다.
 */
public enum LlmPurpose {
    SAJU,
    COMPATIBILITY,
    DATING
}
