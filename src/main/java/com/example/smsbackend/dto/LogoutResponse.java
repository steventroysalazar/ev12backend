package com.example.smsbackend.dto;

public record LogoutResponse(
    boolean success,
    String message,
    LoginContextResponse logoutContext
) {
}
