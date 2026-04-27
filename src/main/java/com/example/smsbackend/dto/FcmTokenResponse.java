package com.example.smsbackend.dto;

import java.time.Instant;

public record FcmTokenResponse(
    boolean success,
    Long userId,
    String deviceId,
    Instant fcmTokenUpdatedAt
) {
}
