package com.example.smsbackend.dto;

import java.time.Instant;

public record SimStatusResponse(
    Long deviceId,
    String simIccid,
    String msisdn,
    String status,
    boolean activated,
    Instant updatedAt
) {
}
