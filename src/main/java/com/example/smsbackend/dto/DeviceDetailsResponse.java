package com.example.smsbackend.dto;

public record DeviceDetailsResponse(
    Long id,
    String name,
    String phoneNumber,
    Long userId,
    String ownerName,
    Long locationId,
    String locationName
) {
}
