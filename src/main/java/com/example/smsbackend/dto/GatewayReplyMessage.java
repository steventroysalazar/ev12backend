package com.example.smsbackend.dto;

public record GatewayReplyMessage(
    long id,
    String from,
    String message,
    long date
) {
}
