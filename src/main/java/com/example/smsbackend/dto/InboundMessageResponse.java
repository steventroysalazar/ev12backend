package com.example.smsbackend.dto;

public record InboundMessageResponse(
    long id,
    String from,
    String text,
    String receivedAt
) {
}
