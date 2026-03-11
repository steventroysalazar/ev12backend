package com.example.smsbackend.dto;

public record UpdateDeviceRequest(
    String name,
    String phoneNumber,
    Long userId,
    DeviceProtocolSettings protocolSettings
) {
}
