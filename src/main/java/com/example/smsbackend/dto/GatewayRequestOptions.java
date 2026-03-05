package com.example.smsbackend.dto;

public record GatewayRequestOptions(
    String baseUrl,
    String token
) {
}
