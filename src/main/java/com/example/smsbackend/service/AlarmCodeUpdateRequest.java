package com.example.smsbackend.service;

import java.time.Instant;

public record AlarmCodeUpdateRequest(
    String externalDeviceId,
    String alarmCode,
    Instant updatedAt
) {
}
