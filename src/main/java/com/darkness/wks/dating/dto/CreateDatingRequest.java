package com.darkness.wks.dating.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDatingRequest(@NotNull UUID candidateId) {
}
