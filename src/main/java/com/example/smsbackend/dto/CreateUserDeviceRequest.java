package com.example.smsbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserDeviceRequest(
    @NotBlank String name,
    @NotBlank String phoneNumber,
    @NotBlank String deviceId
) {
}
