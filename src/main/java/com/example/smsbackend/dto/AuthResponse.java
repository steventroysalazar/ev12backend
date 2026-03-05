package com.example.smsbackend.dto;

public record AuthResponse(
    boolean success,
    String token,
    UserResponse user
) {
}
