package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertFcmTokenRequest(
    @NotNull Long userId,
    @NotBlank @JsonAlias("fcm_token") String fcmToken,
    @JsonAlias("device_id") String deviceId,
    @JsonAlias("os_type") String osType,
    @JsonAlias("api_version") String apiVersion
) {
}
