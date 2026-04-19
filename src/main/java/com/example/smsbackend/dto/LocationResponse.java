package com.example.smsbackend.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record LocationResponse(
    Long id,
    String name,
    String details,
    Long companyId,
    JsonNode alarmReceiverConfig,
    long usersCount,
    long devicesCount
) {
}
