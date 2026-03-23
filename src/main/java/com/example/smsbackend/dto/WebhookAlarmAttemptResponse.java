package com.example.smsbackend.dto;

import java.time.Instant;

public record WebhookAlarmAttemptResponse(
    Integer candidateIndex,
    String externalDeviceId,
    String alarmCode,
    Instant eventTimestamp,
    String action,
    String reason
) {
}
