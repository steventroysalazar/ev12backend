package com.example.smsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLocationRequest(
    @NotBlank String name,
    String details,
    @NotNull Long companyId
) {
}
