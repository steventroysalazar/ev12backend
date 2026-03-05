package com.example.smsbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
    @NotBlank String to,
    @NotBlank String message,
    Integer slot
) {
}
