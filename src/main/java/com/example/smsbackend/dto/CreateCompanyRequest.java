package com.example.smsbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
    @NotBlank String name,
    String details
) {
}
