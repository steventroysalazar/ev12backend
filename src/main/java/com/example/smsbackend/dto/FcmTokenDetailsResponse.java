package com.example.smsbackend.dto;

import java.time.Instant;

public record FcmTokenDetailsResponse(
    boolean success,
    Long userId,
    String deviceId,
    String fcmToken,
    Instant fcmTokenUpdatedAt
) {
}
