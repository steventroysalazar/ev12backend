package com.example.smsbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDeviceRequest(
    @NotBlank String name,
    @NotBlank String phoneNumber
) {
}
