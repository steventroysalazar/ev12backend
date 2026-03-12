package com.example.smsbackend.dto;

import java.time.Instant;

public record DeviceConfigStatusResponse(
    Long deviceId,
    String status,
    boolean pending,
    Instant lastSentAt,
    Instant appliedAt,
    Instant nextResendAt,
    String commandPreview
) {
}
