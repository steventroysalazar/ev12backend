package com.example.smsbackend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record CompanyResponse(
    Long id,
    String name,
    String companyName,
    String details,
    String address,
    String city,
    String state,
    String postalCode,
    String country,
    String phone,
    boolean isAlarmReceiverIncluded,
    boolean alarmReceiverEnabled,
    JsonNode alarmReceiverConfig,
    List<String> whitelisted,
    List<String> whitelistedIps,
    long locationsCount,
    long usersCount,
    long devicesCount
) {
}
