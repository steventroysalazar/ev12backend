package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;

public record LogoutRequest(
    Long userId,
    @Email String email,
    @JsonAlias("username") String username,
    @JsonAlias("grant_type") String grantType,
    String scope,
    @JsonAlias("os_type") String osType,
    @JsonAlias("api_version") String apiVersion,
    @JsonAlias("device_id") String deviceId
) {
}
