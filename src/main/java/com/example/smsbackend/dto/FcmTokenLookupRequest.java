package com.example.smsbackend.dto;

import jakarta.validation.constraints.NotNull;

public record FcmTokenLookupRequest(
    @NotNull Long userId
) {
}
