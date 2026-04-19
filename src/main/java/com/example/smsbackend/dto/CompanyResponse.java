package com.example.smsbackend.dto;

public record CompanyResponse(
    Long id,
    String name,
    String details,
    long locationsCount,
    long usersCount,
    long devicesCount
) {
}
