package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

public record GeofenceToggleLogRequest(
    @NotNull
    @JsonAlias("device_id")
    Long deviceId,
    @NotNull
    @JsonAlias("acted_by_user_id")
    Long actedByUserId,
    @NotNull
    @JsonAlias("enabled")
    Boolean enabled,
    @NotNull
    @JsonAlias("confirmed")
    Boolean confirmed
) {
}
