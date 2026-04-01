package com.example.smsbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "device_alarm_logs",
    indexes = {
        @Index(name = "idx_device_alarm_logs_device_id", columnList = "device_id"),
        @Index(name = "idx_device_alarm_logs_event_at", columnList = "eventAt")
    }
)
public class DeviceAlarmLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "external_device_id", length = 64)
    private String externalDeviceId;

    @Column(name = "alarm_code", length = 64)
    private String alarmCode;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "note", length = 255)
    private String note;

    public Long getId() { return id; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public String getExternalDeviceId() { return externalDeviceId; }
    public void setExternalDeviceId(String externalDeviceId) { this.externalDeviceId = externalDeviceId; }
    public String getAlarmCode() { return alarmCode; }
    public void setAlarmCode(String alarmCode) { this.alarmCode = alarmCode; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Instant getEventAt() { return eventAt; }
    public void setEventAt(Instant eventAt) { this.eventAt = eventAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
