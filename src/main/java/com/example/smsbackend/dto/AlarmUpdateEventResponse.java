package com.example.smsbackend.dto;

import java.time.Instant;

public record AlarmUpdateEventResponse(
    Long deviceId,
    String externalDeviceId,
    String alarmCode,
    Instant updatedAt
) {
}
