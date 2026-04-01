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
    name = "device_location_breadcrumbs",
    indexes = {
        @Index(name = "idx_device_location_breadcrumbs_device_id", columnList = "device_id"),
        @Index(name = "idx_device_location_breadcrumbs_captured_at", columnList = "capturedAt")
    }
)
public class DeviceLocationBreadcrumb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "external_device_id", length = 64)
    private String externalDeviceId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "gateway_message_id")
    private Long gatewayMessageId;

    public Long getId() { return id; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public String getExternalDeviceId() { return externalDeviceId; }
    public void setExternalDeviceId(String externalDeviceId) { this.externalDeviceId = externalDeviceId; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    public Long getGatewayMessageId() { return gatewayMessageId; }
    public void setGatewayMessageId(Long gatewayMessageId) { this.gatewayMessageId = gatewayMessageId; }
}
