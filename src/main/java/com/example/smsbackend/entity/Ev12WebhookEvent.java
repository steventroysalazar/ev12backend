package com.example.smsbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ev12_webhook_events")
public class Ev12WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String deviceId;

    @Column(length = 64)
    private String imei;

    @Column
    private Integer batteryLevel;

    @Column(length = 64)
    private String signalQuality;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column
    private Instant deviceTimestamp;

    @Lob
    @Column(nullable = false)
    private String payloadJson;

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public Integer getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(Integer batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public String getSignalQuality() {
        return signalQuality;
    }

    public void setSignalQuality(String signalQuality) {
        this.signalQuality = signalQuality;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getDeviceTimestamp() {
        return deviceTimestamp;
    }

    public void setDeviceTimestamp(Instant deviceTimestamp) {
        this.deviceTimestamp = deviceTimestamp;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
