package com.example.smsbackend.dto;

import java.util.List;

public record AlertLogFiltersResponse(
    List<String> alarmCodes,
    List<String> actions,
    List<String> sources
) {
}
