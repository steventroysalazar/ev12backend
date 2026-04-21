package com.example.smsbackend.dto;

import java.time.Instant;

public record ResendImeiResponse(
    boolean success,
    Long deviceId,
    String phoneNumber,
    String command,
    Instant sentAt
) {
}
