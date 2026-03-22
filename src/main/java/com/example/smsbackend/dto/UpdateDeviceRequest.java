package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public class UpdateDeviceRequest {

    private String name;
    private String phoneNumber;
    private String externalDeviceId;
    private Long userId;
    private DeviceProtocolSettings protocolSettings;
    private String alarmCode;
    private Instant alarmCancelledAt;

    @JsonIgnore
    private boolean alarmCodeProvided;

    @JsonIgnore
    private boolean alarmCancelledAtProvided;

    public UpdateDeviceRequest() {
    }

    public UpdateDeviceRequest(
        String name,
        String phoneNumber,
        String externalDeviceId,
        Long userId,
        DeviceProtocolSettings protocolSettings
    ) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.externalDeviceId = externalDeviceId;
        this.userId = userId;
        this.protocolSettings = protocolSettings;
    }

    public String name() {
        return name;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public String externalDeviceId() {
        return externalDeviceId;
    }

    public Long userId() {
        return userId;
    }

    public DeviceProtocolSettings protocolSettings() {
        return protocolSettings;
    }

    public String alarmCode() {
        return alarmCode;
    }

    public Instant alarmCancelledAt() {
        return alarmCancelledAt;
    }

    public boolean alarmCodeProvided() {
        return alarmCodeProvided;
    }

    public boolean alarmCancelledAtProvided() {
        return alarmCancelledAtProvided;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @JsonAlias("external_device_id")
    public void setExternalDeviceId(String externalDeviceId) {
        this.externalDeviceId = externalDeviceId;
    }

    @JsonAlias("user_id")
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @JsonAlias("protocol_settings")
    public void setProtocolSettings(DeviceProtocolSettings protocolSettings) {
        this.protocolSettings = protocolSettings;
    }

    @JsonAlias("alarm_code")
    public void setAlarmCode(String alarmCode) {
        this.alarmCode = alarmCode;
        this.alarmCodeProvided = true;
    }

    @JsonAlias("alarm_cancelled_at")
    public void setAlarmCancelledAt(Instant alarmCancelledAt) {
        this.alarmCancelledAt = alarmCancelledAt;
        this.alarmCancelledAtProvided = true;
    }
}
