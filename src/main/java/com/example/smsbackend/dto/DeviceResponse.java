package com.example.smsbackend.dto;

import java.time.Instant;

public record DeviceResponse(
    Long id,
    Long userId,
    Long companyId,
    String name,
    String phoneNumber,
    String externalDeviceId,
    String simIccid,
    String simStatus,
    boolean simActivated,
    Instant simStatusUpdatedAt,
    String alarmCode,
    Instant alarmTriggeredAt,
    Instant alarmCancelledAt,
    Instant lastPowerOnAt,
    Instant lastPowerOffAt,
    Instant lastDisconnectedAt,
    Double latitude,
    Double longitude,
    Instant locationUpdatedAt,
    String branchAccountNumber,
    DeviceProtocolSettings protocolSettings,
    String configStatus,
    Instant configLastSentAt,
    Instant configAppliedAt
) {
}
