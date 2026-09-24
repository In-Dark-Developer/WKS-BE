package com.darkness.wks.dating.dto;

import jakarta.validation.constraints.NotBlank;

public record DatingPhotoUploadRequest(@NotBlank String contentType) {
}
