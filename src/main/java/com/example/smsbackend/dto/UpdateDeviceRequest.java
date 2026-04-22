package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public class UpdateDeviceRequest {

    private String name;
    private String phoneNumber;
    private String externalDeviceId;
    private String simIccid;
    private Long userId;
    private Long locationId;
    private Boolean clearLocation;
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
        String simIccid,
        Long userId,
        DeviceProtocolSettings protocolSettings
    ) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.externalDeviceId = externalDeviceId;
        this.simIccid = simIccid;
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

    public String simIccid() {
        return simIccid;
    }

    public Long userId() {
        return userId;
    }

    public Long locationId() {
        return locationId;
    }

    public Boolean clearLocation() {
        return clearLocation;
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

    @JsonAlias("sim_iccid")
    public void setSimIccid(String simIccid) {
        this.simIccid = simIccid;
    }

    @JsonAlias("user_id")
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @JsonAlias("location_id")
    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    @JsonAlias("clear_location")
    public void setClearLocation(Boolean clearLocation) {
        this.clearLocation = clearLocation;
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
