package com.example.smsbackend.service;

import com.example.smsbackend.dto.AlarmUpdateEventResponse;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AlarmCodeUpdateWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmCodeUpdateWorkerService.class);
    private static final String SOS_ALERT = "SOS Alert";

    private final DeviceRepository deviceRepository;
    private final AlarmStreamService alarmStreamService;
    private final DeviceTelemetryLogService deviceTelemetryLogService;

    public AlarmCodeUpdateWorkerService(
        DeviceRepository deviceRepository,
        AlarmStreamService alarmStreamService,
        DeviceTelemetryLogService deviceTelemetryLogService
    ) {
        this.deviceRepository = deviceRepository;
        this.alarmStreamService = alarmStreamService;
        this.deviceTelemetryLogService = deviceTelemetryLogService;
    }

    public AlarmCodeUpdateResult applyNow(AlarmCodeUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.externalDeviceId())) {
            LOGGER.warn("Skipping alarm update enqueue because request or externalDeviceId is missing.");
            return AlarmCodeUpdateResult.ignored("missing externalDeviceId");
        }
        LOGGER.info(
            "Applying alarm update: externalDeviceId='{}', alarmCode='{}', eventTimestamp='{}'",
            request.externalDeviceId(),
            request.alarmCode(),
            request.updatedAt()
        );
        try {
            return process(request);
        } catch (Exception exception) {
            LOGGER.warn("Unable to process alarm update request.", exception);
            return AlarmCodeUpdateResult.ignored("processing error: " + exception.getClass().getSimpleName());
        }
    }

    public void recordPowerLifecycleEvent(String externalDeviceId, String alarmCode, Instant eventTimestamp) {
        if (!StringUtils.hasText(externalDeviceId) || !StringUtils.hasText(alarmCode)) {
            return;
        }
        Device device = resolveDevice(externalDeviceId);
        if (device == null) {
            return;
        }

        Instant eventAt = eventTimestamp == null ? Instant.now() : eventTimestamp;
        String normalized = alarmCode.toLowerCase();
        if (normalized.contains("power on")) {
            device.setLastPowerOnAt(eventAt);
            deviceTelemetryLogService.logAlarmEvent(
                device,
                "POWER_ON",
                "WEBHOOK",
                alarmCode,
                eventAt,
                "Power on alarm received from EV12 webhook"
            );
        } else if (normalized.contains("power off")) {
            device.setLastPowerOffAt(eventAt);
            deviceTelemetryLogService.logAlarmEvent(
                device,
                "POWER_OFF",
                "WEBHOOK",
                alarmCode,
                eventAt,
                "Power off alarm received from EV12 webhook"
            );
        } else {
            return;
        }
        deviceRepository.save(device);
    }

    public void recordDisconnectedStatus(String externalDeviceId, Instant eventTimestamp) {
        if (!StringUtils.hasText(externalDeviceId)) {
            return;
        }
        Device device = resolveDevice(externalDeviceId);
        if (device == null) {
            return;
        }
        Instant eventAt = eventTimestamp == null ? Instant.now() : eventTimestamp;
        device.setLastDisconnectedAt(eventAt);
        deviceRepository.save(device);
        deviceTelemetryLogService.logAlarmEvent(
            device,
            "DEVICE_DISCONNECTED",
            "WEBHOOK",
            null,
            eventAt,
            "Device disconnected status received from EV12 webhook"
        );
    }

    AlarmCodeUpdateResult process(AlarmCodeUpdateRequest request) {
        Device device = resolveDevice(request.externalDeviceId());
        if (device == null) {
            LOGGER.warn("No device found for EV12 deviceId '{}' while applying alarm code '{}'", request.externalDeviceId(), request.alarmCode());
            return AlarmCodeUpdateResult.ignored("no matching device found by externalDeviceId");
        }
        if (isStaleSosUpdate(device, request)) {
            LOGGER.info(
                "Ignoring stale SOS update for device '{}'. requestTimestamp='{}', cancelledAt='{}'",
                device.getExternalDeviceId(),
                request.updatedAt(),
                device.getAlarmCancelledAt()
            );
            return AlarmCodeUpdateResult.ignored("stale sos event older than alarmCancelledAt");
        }

        if (sameAlarmCode(device.getAlarmCode(), request.alarmCode())) {
            LOGGER.info(
                "Ignoring unchanged alarm code for device '{}'. current='{}', incoming='{}'",
                device.getExternalDeviceId(),
                device.getAlarmCode(),
                request.alarmCode()
            );
            return AlarmCodeUpdateResult.ignored("alarm code unchanged");
        }

        device.setAlarmCode(request.alarmCode());
        Device savedDevice = deviceRepository.save(device);
        LOGGER.info(
            "Alarm code updated: deviceId='{}', externalDeviceId='{}', alarmCode='{}'",
            savedDevice.getId(),
            savedDevice.getExternalDeviceId(),
            savedDevice.getAlarmCode()
        );
        Instant updatedAt = request.updatedAt() == null ? Instant.now() : request.updatedAt();
        deviceTelemetryLogService.logAlarmEvent(
            savedDevice,
            "ALARM_TRIGGERED",
            "WEBHOOK",
            savedDevice.getAlarmCode(),
            updatedAt,
            "Alarm state updated from EV12 webhook"
        );
        alarmStreamService.publish(new AlarmUpdateEventResponse(
            savedDevice.getId(),
            savedDevice.getExternalDeviceId(),
            savedDevice.getAlarmCode(),
            updatedAt
        ));
        return AlarmCodeUpdateResult.applied(
            savedDevice.getId(),
            savedDevice.getExternalDeviceId(),
            savedDevice.getAlarmCode()
        );
    }

    private boolean isStaleSosUpdate(Device device, AlarmCodeUpdateRequest request) {
        if (!SOS_ALERT.equalsIgnoreCase(String.valueOf(request.alarmCode()))) {
            return false;
        }
        if (device.getAlarmCancelledAt() == null) {
            return false;
        }

        Instant eventTime = request.updatedAt() == null ? Instant.now() : request.updatedAt();
        return !eventTime.isAfter(device.getAlarmCancelledAt());
    }

    private Device resolveDevice(String externalDeviceId) {
        Device exact = deviceRepository.findByExternalDeviceId(externalDeviceId).orElse(null);
        if (exact != null) {
            return exact;
        }

        String normalizedIncomingId = normalizeDeviceId(externalDeviceId);
        if (!StringUtils.hasText(normalizedIncomingId)) {
            return null;
        }

        List<Device> matches = deviceRepository.findAll().stream()
            .filter(device -> normalizedIncomingId.equals(normalizeDeviceId(device.getExternalDeviceId())))
            .limit(2)
            .toList();

        if (matches.size() == 1) {
            return matches.get(0);
        }
        return null;
    }

    private String normalizeDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        return deviceId.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private boolean sameAlarmCode(String currentAlarmCode, String nextAlarmCode) {
        if (currentAlarmCode == null && nextAlarmCode == null) {
            return true;
        }
        if (currentAlarmCode == null || nextAlarmCode == null) {
            return false;
        }
        return Objects.equals(currentAlarmCode.toLowerCase(), nextAlarmCode.toLowerCase());
    }
}
