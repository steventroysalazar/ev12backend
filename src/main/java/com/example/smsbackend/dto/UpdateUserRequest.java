package com.example.smsbackend.dto;

import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
    String firstName,
    String lastName,
    @Email String email,
    String contactNumber,
    String address,
    Integer userRole,
    Long locationId,
    Boolean clearLocation,
    Long managerId,
    Boolean clearManager
) {
}
