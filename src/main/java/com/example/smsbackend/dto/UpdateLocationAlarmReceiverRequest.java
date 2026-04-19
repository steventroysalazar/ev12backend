package com.example.smsbackend.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateLocationAlarmReceiverRequest(
    String accountNumber,
    Boolean en,
    JsonNode users,
    Boolean toggleCompanyAlarmReceiver
) {
}
