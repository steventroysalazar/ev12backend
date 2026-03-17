package com.example.smsbackend.dto;

public record UpdateDeviceRequest(
    String name,
    String phoneNumber,
    String externalDeviceId,
    Long userId,
    DeviceProtocolSettings protocolSettings
) {
}
