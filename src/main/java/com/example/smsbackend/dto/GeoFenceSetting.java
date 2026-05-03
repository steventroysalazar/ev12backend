package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record GeoFenceSetting(
    Integer slot,
    String enabled,
    String mode,
    String radius,
    @JsonAlias({"latitude", "lat"})
    Double latitude,
    @JsonAlias({"longitude", "lng", "lon"})
    Double longitude
) {
}
