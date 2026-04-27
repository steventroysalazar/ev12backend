package com.example.smsbackend.dto;

import java.time.Instant;

public record LoginLogResponse(
    Long id,
    String eventType,
    Long userId,
    String loginIdentifier,
    String grantType,
    String scope,
    String osType,
    String apiVersion,
    String deviceId,
    String ipAddress,
    String userAgent,
    Instant createdAt
) {
}
