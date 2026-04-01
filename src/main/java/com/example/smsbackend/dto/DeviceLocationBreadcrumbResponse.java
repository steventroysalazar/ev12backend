package com.example.smsbackend.dto;

import java.time.Instant;

public record DeviceLocationBreadcrumbResponse(
    Long id,
    Long deviceId,
    String externalDeviceId,
    Double latitude,
    Double longitude,
    String source,
    Instant capturedAt,
    Long gatewayMessageId
) {
}
