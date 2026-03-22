package com.example.smsbackend.service;

import com.example.smsbackend.dto.AlarmUpdateEventResponse;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
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
            return;
        }
        queue.offer(request);
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
        Device device = deviceRepository.findByExternalDeviceId(request.externalDeviceId())
            .orElse(null);
        if (device == null) {
            return;
        }
        if (sameAlarmCode(device.getAlarmCode(), request.alarmCode())) {
            return;
        }

        device.setAlarmCode(request.alarmCode());
        Device savedDevice = deviceRepository.save(device);
        Instant updatedAt = request.updatedAt() == null ? Instant.now() : request.updatedAt();
        alarmStreamService.publish(new AlarmUpdateEventResponse(
            savedDevice.getId(),
            savedDevice.getExternalDeviceId(),
            savedDevice.getAlarmCode(),
            updatedAt
        ));
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
