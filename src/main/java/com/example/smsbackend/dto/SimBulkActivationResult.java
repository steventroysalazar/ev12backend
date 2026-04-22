package com.example.smsbackend.dto;

public record SimBulkActivationResult(
    Long deviceId,
    boolean success,
    String error,
    SimStatusResponse status
) {
}
