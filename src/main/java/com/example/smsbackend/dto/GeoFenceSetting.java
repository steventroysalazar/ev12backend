package com.example.smsbackend.dto;

public record GeoFenceSetting(
    Integer slot,
    String enabled,
    String mode,
    String radius
) {
}
