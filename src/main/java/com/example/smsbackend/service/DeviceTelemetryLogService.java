package com.example.smsbackend.service;

import com.example.smsbackend.dto.AlertLogFiltersResponse;
import com.example.smsbackend.dto.DeviceAlarmLogResponse;
import com.example.smsbackend.dto.DeviceLocationBreadcrumbResponse;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.entity.DeviceAlarmLog;
import com.example.smsbackend.entity.DeviceLocationBreadcrumb;
import com.example.smsbackend.repository.DeviceAlarmLogRepository;
import com.example.smsbackend.repository.DeviceLocationBreadcrumbRepository;
import com.example.smsbackend.repository.DeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DeviceTelemetryLogService {

    private static final Pattern DECIMAL_COORDINATE_PATTERN = Pattern.compile("(-?\\d{1,3}\\.\\d+)[^\\d-]+(-?\\d{1,3}\\.\\d+)");

    private final DeviceAlarmLogRepository deviceAlarmLogRepository;
    private final DeviceLocationBreadcrumbRepository deviceLocationBreadcrumbRepository;
    private final DeviceRepository deviceRepository;

    public DeviceTelemetryLogService(
        DeviceAlarmLogRepository deviceAlarmLogRepository,
        DeviceLocationBreadcrumbRepository deviceLocationBreadcrumbRepository,
        DeviceRepository deviceRepository
    ) {
        this.deviceAlarmLogRepository = deviceAlarmLogRepository;
        this.deviceLocationBreadcrumbRepository = deviceLocationBreadcrumbRepository;
        this.deviceRepository = deviceRepository;
    }

    public void logAlarmEvent(Device device, String action, String source, String alarmCode, Instant eventAt, String note) {
        if (device == null) {
            return;
        }
        DeviceAlarmLog log = new DeviceAlarmLog();
        log.setDevice(device);
        log.setExternalDeviceId(device.getExternalDeviceId());
        log.setAction(action);
        log.setSource(source);
        log.setAlarmCode(alarmCode);
        log.setLatitude(device.getLatitude());
        log.setLongitude(device.getLongitude());
        log.setEventAt(eventAt == null ? Instant.now() : eventAt);
        log.setNote(note);
        deviceAlarmLogRepository.save(log);
    }

    public void logLocation(Device device, String source, Double latitude, Double longitude, Instant capturedAt, Long gatewayMessageId) {
        if (device == null || latitude == null || longitude == null) {
            return;
        }
        DeviceLocationBreadcrumb breadcrumb = new DeviceLocationBreadcrumb();
        breadcrumb.setDevice(device);
        breadcrumb.setExternalDeviceId(device.getExternalDeviceId());
        breadcrumb.setLatitude(latitude);
        breadcrumb.setLongitude(longitude);
        breadcrumb.setSource(source);
        breadcrumb.setCapturedAt(capturedAt == null ? Instant.now() : capturedAt);
        breadcrumb.setGatewayMessageId(gatewayMessageId);
        deviceLocationBreadcrumbRepository.save(breadcrumb);
    }

    public void logLocationFromSms(Device device, long gatewayMessageId, String smsText, Instant capturedAt) {
        if (device == null || smsText == null || smsText.isBlank()) {
            return;
        }
        Matcher matcher = DECIMAL_COORDINATE_PATTERN.matcher(smsText);
        while (matcher.find()) {
            Double latitude = parseCoordinate(matcher.group(1), true);
            Double longitude = parseCoordinate(matcher.group(2), false);
            if (latitude != null && longitude != null) {
                logLocation(device, "SMS", latitude, longitude, capturedAt, gatewayMessageId);
                return;
            }
        }
    }

    public List<String> listActiveAlertsLookup() {
        return deviceRepository.findDistinctActiveAlarmCodes();
    }

    public AlertLogFiltersResponse listAlertLogFiltersLookup() {
        return new AlertLogFiltersResponse(
            deviceAlarmLogRepository.findDistinctAlarmCodes(),
            deviceAlarmLogRepository.findDistinctActions(),
            deviceAlarmLogRepository.findDistinctSources()
        );
    }

    public List<DeviceAlarmLogResponse> listAlarmLogs(Long deviceId) {
        return deviceAlarmLogRepository.findByDeviceIdOrderByEventAtDesc(deviceId).stream()
            .map(log -> new DeviceAlarmLogResponse(
                log.getId(),
                log.getDevice() == null ? null : log.getDevice().getId(),
                log.getExternalDeviceId(),
                log.getAlarmCode(),
                log.getAction(),
                log.getSource(),
                log.getLatitude(),
                log.getLongitude(),
                log.getEventAt(),
                log.getNote()
            ))
            .toList();
    }

    public List<DeviceLocationBreadcrumbResponse> listLocationBreadcrumbs(Long deviceId) {
        return deviceLocationBreadcrumbRepository.findByDeviceIdOrderByCapturedAtDesc(deviceId).stream()
            .map(log -> new DeviceLocationBreadcrumbResponse(
                log.getId(),
                log.getDevice() == null ? null : log.getDevice().getId(),
                log.getExternalDeviceId(),
                log.getLatitude(),
                log.getLongitude(),
                log.getSource(),
                log.getCapturedAt(),
                log.getGatewayMessageId()
            ))
            .toList();
    }

    private Double parseCoordinate(String raw, boolean latitude) {
        try {
            double value = Double.parseDouble(raw);
            if (latitude && (value < -90 || value > 90)) {
                return null;
            }
            if (!latitude && (value < -180 || value > 180)) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
