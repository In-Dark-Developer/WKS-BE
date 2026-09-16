package com.darkness.wks.signup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "사전등록 사진 업로드용 presigned URL 발급 요청")
public record PhotoUploadUrlRequest(
        @Schema(description = "업로드할 파일의 Content-Type", example = "image/jpeg", allowableValues = {"image/jpeg", "image/png", "image/webp"})
        @NotBlank(message = "contentType은 필수입니다.")
        @Pattern(regexp = "^image/(jpeg|png|webp)$", message = "contentType은 image/jpeg, image/png, image/webp 중 하나여야 합니다.")
        String contentType
) {
}
