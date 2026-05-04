package com.example.smsbackend.dto;

import java.time.Instant;

public record GeofenceToggleLogResponse(
    Long id,
    Long deviceId,
    String deviceExternalId,
    Long actedByUserId,
    String actedByEmail,
    boolean enabled,
    boolean confirmed,
    Instant createdAt
) {
}
