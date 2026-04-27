package com.example.smsbackend.dto;

import java.time.Instant;

public record LoginContextResponse(
    String loginIdentifier,
    String grantType,
    String scope,
    String osType,
    String apiVersion,
    String deviceId,
    String ipAddress,
    String userAgent,
    Instant loggedAt
) {
}
