package com.example.smsbackend.dto;

public record UserLookupResponse(
    Long id,
    String firstName,
    String lastName,
    String email,
    Integer userRole
) {
}
