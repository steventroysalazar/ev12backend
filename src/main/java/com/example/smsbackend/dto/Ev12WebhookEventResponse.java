package com.example.smsbackend.dto;

import java.time.Instant;

public record Ev12WebhookEventResponse(
    Long id,
    Instant receivedAt,
    String payloadJson
) {
}
