package com.darkness.wks.signup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사진 업로드용 presigned URL 발급 결과")
public record PhotoUploadUrlResponse(
        @Schema(description = "이 URL로 파일 바이트를 그대로 PUT 요청한다. Content-Type 헤더를 요청 시 보낸 값과 동일하게 설정해야 한다.",
                example = "https://wks-photos.s3.ap-northeast-2.amazonaws.com/signup-photos/3f2a9c1e-....jpg?X-Amz-...")
        String uploadUrl,

        @Schema(description = "업로드 완료 후 `POST /api/signups` 요청의 photoKey에 그대로 넣는다.",
                example = "signup-photos/3f2a9c1e-....jpg")
        String photoKey,

        @Schema(description = "uploadUrl 유효 시간(초)", example = "600")
        int expiresInSeconds
) {
}
