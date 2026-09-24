package com.darkness.wks.dating.dto;

import java.util.UUID;

public record DatingPhotoUploadResponse(String uploadUrl, UUID photoId, int expiresInSeconds) {
}
