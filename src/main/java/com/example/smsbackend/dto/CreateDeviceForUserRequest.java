package com.example.smsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDeviceForUserRequest(
    @NotNull Long userId,
    @NotBlank String name,
    @NotBlank String phoneNumber
) {
}
