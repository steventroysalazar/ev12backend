package com.example.smsbackend.dto;

import java.time.Instant;

public record DeviceAlarmLogResponse(
    Long id,
    Long deviceId,
    String externalDeviceId,
    String alarmCode,
    String action,
    String source,
    Double latitude,
    Double longitude,
    Instant eventAt,
    String note
) {
}
