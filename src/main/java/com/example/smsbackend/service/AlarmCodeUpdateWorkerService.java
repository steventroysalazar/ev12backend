package com.example.smsbackend.service;

import com.example.smsbackend.dto.AlarmUpdateEventResponse;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
    private final BlockingQueue<AlarmCodeUpdateRequest> queue = new LinkedBlockingQueue<>();

    private Thread workerThread;
    private volatile boolean running = true;

    public AlarmCodeUpdateWorkerService(DeviceRepository deviceRepository, AlarmStreamService alarmStreamService) {
        this.deviceRepository = deviceRepository;
        this.alarmStreamService = alarmStreamService;
    }

    @PostConstruct
    void start() {
        workerThread = new Thread(this::runLoop, "alarm-code-update-worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    public void enqueue(AlarmCodeUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.externalDeviceId())) {
            LOGGER.warn("Skipping alarm update enqueue because request or externalDeviceId is missing.");
            return;
        }
        queue.offer(request);
        LOGGER.info(
            "Alarm update enqueued: externalDeviceId='{}', alarmCode='{}', eventTimestamp='{}'",
            request.externalDeviceId(),
            request.alarmCode(),
            request.updatedAt()
        );
    }

    void runLoop() {
        while (running) {
            try {
                AlarmCodeUpdateRequest request = queue.take();
                process(request);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                LOGGER.warn("Unable to process alarm update request.", exception);
            }
        }
    }

    void process(AlarmCodeUpdateRequest request) {
        Device device = resolveDevice(request.externalDeviceId());
        if (device == null) {
            LOGGER.warn("No device found for EV12 deviceId '{}' while applying alarm code '{}'", request.externalDeviceId(), request.alarmCode());
            return;
        }
        if (isStaleSosUpdate(device, request)) {
            LOGGER.info(
                "Ignoring stale SOS update for device '{}'. requestTimestamp='{}', cancelledAt='{}'",
                device.getExternalDeviceId(),
                request.updatedAt(),
                device.getAlarmCancelledAt()
            );
            return;
        }

        if (sameAlarmCode(device.getAlarmCode(), request.alarmCode())) {
            LOGGER.info(
                "Ignoring unchanged alarm code for device '{}'. current='{}', incoming='{}'",
                device.getExternalDeviceId(),
                device.getAlarmCode(),
                request.alarmCode()
            );
            return;
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
        alarmStreamService.publish(new AlarmUpdateEventResponse(
            savedDevice.getId(),
            savedDevice.getExternalDeviceId(),
            savedDevice.getAlarmCode(),
            updatedAt
        ));
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
