package com.example.smsbackend.dto;

public record DeviceContactSettings(
    Integer slot,
    Boolean smsEnabled,
    Boolean callEnabled,
    String phone,
    String name
) {
}

