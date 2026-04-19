package com.example.smsbackend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record UpdateCompanyAlarmReceiverRequest(
    JsonNode alarmReceiverConfig,
    List<String> dnsWhitelist,
    List<String> ipWhitelist,
    Boolean alarmReceiverEnabled
) {
}
