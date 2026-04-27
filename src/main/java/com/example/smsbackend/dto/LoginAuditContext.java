package com.example.smsbackend.dto;

public record LoginAuditContext(
    String ipAddress,
    String userAgent
) {
}
