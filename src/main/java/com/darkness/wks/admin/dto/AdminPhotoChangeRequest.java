package com.darkness.wks.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminPhotoChangeRequest(@NotNull UUID photoId) {
}
