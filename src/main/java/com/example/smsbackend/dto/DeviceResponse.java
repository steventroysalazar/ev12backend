package com.example.smsbackend.dto;

import java.time.Instant;

public record DeviceResponse(
    Long id,
    Long userId,
    Long companyId,
    String name,
    String phoneNumber,
    String externalDeviceId,
    String alarmCode,
    Instant alarmCancelledAt,
    Instant lastPowerOnAt,
    Instant lastPowerOffAt,
    Instant lastDisconnectedAt,
    Double latitude,
    Double longitude,
    Instant locationUpdatedAt,
    DeviceProtocolSettings protocolSettings,
    String configStatus,
    Instant configLastSentAt,
    Instant configAppliedAt
) {
}
