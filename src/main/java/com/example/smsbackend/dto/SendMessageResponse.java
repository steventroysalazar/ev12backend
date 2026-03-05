package com.example.smsbackend.dto;

public record SendMessageResponse(
    boolean success,
    String info
) {
}
