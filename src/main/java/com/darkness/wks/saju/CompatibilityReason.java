package com.darkness.wks.saju;

/** 궁합 상세 이유 세 질문의 답 (plan §5.10). 두 사람이 같은 글을 본다 */
public record CompatibilityReason(
        String why,       // 왜 나에게 귀인(찰떡·벗·스침)일까요?
        String together,  // 둘이 만나게 된다면?
        String conflict   // 둘이 싸우게 된다면?
) {
}
