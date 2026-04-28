package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Email String email,
    @JsonAlias("username") String username,
    @NotBlank String password,
    @JsonAlias("grant_type") String grantType,
    String scope,
    @JsonAlias("os_type") String osType,
    @JsonAlias("os_version") String osVersion,
    @JsonAlias("api_version") String apiVersion,
    @JsonAlias("device_id") String deviceId
) {
}
