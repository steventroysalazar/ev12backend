package com.example.smsbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLocationRequest(
    @NotBlank String name,
    String details
) {
}
