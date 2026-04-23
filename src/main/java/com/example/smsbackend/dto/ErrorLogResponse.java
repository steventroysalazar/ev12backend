package com.example.smsbackend.dto;

import java.time.Instant;

public record ErrorLogResponse(
    Long id,
    String method,
    String path,
    int statusCode,
    String errorType,
    String errorMessage,
    String stackTrace,
    Instant occurredAt
) {}
