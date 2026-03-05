package com.example.smsbackend.dto;

public record UserResponse(
    Long id,
    String email,
    String firstName,
    String lastName,
    String contactNumber,
    String address,
    Integer userRole,
    Long locationId,
    Long managerId
) {
}
