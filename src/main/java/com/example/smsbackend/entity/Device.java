package com.example.smsbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 32)
    private String phoneNumber;

    @Column(name = "external_device_id", length = 64, unique = true)
    private String externalDeviceId;

    @Column(name = "alarm_code", length = 64)
    private String alarmCode;

    @Column(name = "alarm_cancelled_at")
    private Instant alarmCancelledAt;

    @Column(name = "last_power_on_at")
    private Instant lastPowerOnAt;

    @Column(name = "last_power_off_at")
    private Instant lastPowerOffAt;

    @Column(name = "last_disconnected_at")
    private Instant lastDisconnectedAt;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    @Lob
    @Column(name = "protocol_config")
    private String protocolConfig;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "config_status", nullable = false, length = 24)
    private String configStatus = "IDLE";

    @Lob
    @Column(name = "config_command_preview")
    private String configCommandPreview;

    @Column(name = "config_last_sent_at")
    private Instant configLastSentAt;

    @Column(name = "config_applied_at")
    private Instant configAppliedAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getExternalDeviceId() {
        return externalDeviceId;
    }

    public void setExternalDeviceId(String externalDeviceId) {
        this.externalDeviceId = externalDeviceId;
    }

    public String getAlarmCode() {
        return alarmCode;
    }

    public void setAlarmCode(String alarmCode) {
        this.alarmCode = alarmCode;
    }

    public Instant getAlarmCancelledAt() {
        return alarmCancelledAt;
    }

    public void setAlarmCancelledAt(Instant alarmCancelledAt) {
        this.alarmCancelledAt = alarmCancelledAt;
    }

    public Instant getLastPowerOnAt() {
        return lastPowerOnAt;
    }

    public void setLastPowerOnAt(Instant lastPowerOnAt) {
        this.lastPowerOnAt = lastPowerOnAt;
    }

    public Instant getLastPowerOffAt() {
        return lastPowerOffAt;
    }

    public void setLastPowerOffAt(Instant lastPowerOffAt) {
        this.lastPowerOffAt = lastPowerOffAt;
    }

    public Instant getLastDisconnectedAt() {
        return lastDisconnectedAt;
    }

    public void setLastDisconnectedAt(Instant lastDisconnectedAt) {
        this.lastDisconnectedAt = lastDisconnectedAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Instant getLocationUpdatedAt() {
        return locationUpdatedAt;
    }

    public void setLocationUpdatedAt(Instant locationUpdatedAt) {
        this.locationUpdatedAt = locationUpdatedAt;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getProtocolConfig() {
        return protocolConfig;
    }

    public void setProtocolConfig(String protocolConfig) {
        this.protocolConfig = protocolConfig;
    }

    public String getConfigStatus() {
        return configStatus;
    }

    public void setConfigStatus(String configStatus) {
        this.configStatus = configStatus;
    }

    public String getConfigCommandPreview() {
        return configCommandPreview;
    }

    public void setConfigCommandPreview(String configCommandPreview) {
        this.configCommandPreview = configCommandPreview;
    }

    public Instant getConfigLastSentAt() {
        return configLastSentAt;
    }

    public void setConfigLastSentAt(Instant configLastSentAt) {
        this.configLastSentAt = configLastSentAt;
    }

    public Instant getConfigAppliedAt() {
        return configAppliedAt;
    }

    public void setConfigAppliedAt(Instant configAppliedAt) {
        this.configAppliedAt = configAppliedAt;
    }
}
