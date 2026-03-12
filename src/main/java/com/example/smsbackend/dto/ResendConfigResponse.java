package com.example.smsbackend.dto;

import java.time.Instant;
import java.util.List;

public record ResendConfigResponse(
    boolean success,
    Long deviceId,
    String status,
    Instant sentAt,
    List<SentMessageResponse> messages
) {
}
