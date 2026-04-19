package com.example.smsbackend.dto;

public record LocationResponse(
    Long id,
    String name,
    String details,
    Long companyId,
    long usersCount,
    long devicesCount
) {
}
