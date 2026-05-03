package com.example.smsbackend.dto;

import java.time.Instant;

public record DeviceLocationBreadcrumbResponse(
    Long id,
    Long deviceId,
    String externalDeviceId,
    Double latitude,
    Double longitude,
    String source,
    String alarmCode,
    Instant capturedAt,
    Long gatewayMessageId
) {
}
