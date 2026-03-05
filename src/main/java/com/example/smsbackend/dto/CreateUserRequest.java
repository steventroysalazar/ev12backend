package com.example.smsbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @Email @NotBlank String email,
    @NotBlank String password,
    String contactNumber,
    String address,
    @NotNull Integer userRole,
    Long locationId,
    Long managerId
) {
}
