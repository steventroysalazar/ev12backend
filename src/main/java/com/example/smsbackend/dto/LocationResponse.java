package com.example.smsbackend.dto;

public record LocationResponse(
    Long id,
    String name,
    String details,
    long usersCount,
    long devicesCount
) {
}
