package com.example.smsbackend.service;

public record AlarmCodeUpdateResult(
    String action,
    String reason,
    Long deviceId,
    String externalDeviceId,
    String alarmCode
) {
    public static AlarmCodeUpdateResult applied(Long deviceId, String externalDeviceId, String alarmCode) {
        return new AlarmCodeUpdateResult("applied", "alarm code updated", deviceId, externalDeviceId, alarmCode);
    }

    public static AlarmCodeUpdateResult ignored(String reason) {
        return new AlarmCodeUpdateResult("ignored", reason, null, null, null);
    }
}
