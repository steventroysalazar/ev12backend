package com.example.smsbackend.dto;

public record DeviceResponse(
    Long id,
    Long userId,
    String name,
    String phoneNumber
) {
}
