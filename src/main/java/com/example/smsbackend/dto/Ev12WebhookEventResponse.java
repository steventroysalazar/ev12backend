package com.example.smsbackend.dto;

import java.time.Instant;
import java.util.List;

public record Ev12WebhookEventResponse(
    Long id,
    Instant receivedAt,
    String payloadJson,
    List<WebhookAlarmAttemptResponse> alarmAttempts
) {
}
