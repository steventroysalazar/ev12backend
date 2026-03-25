package com.example.smsbackend.service;

import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceLocationUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceLocationUpdateService.class);

    private final DeviceRepository deviceRepository;

    public DeviceLocationUpdateService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public void applyNow(String externalDeviceId, Double latitude, Double longitude, Instant updatedAt) {
        if (!StringUtils.hasText(externalDeviceId) || latitude == null || longitude == null) {
            return;
        }

        Device device = resolveDevice(externalDeviceId);
        if (device == null) {
            LOGGER.debug("No device found for location update. externalDeviceId='{}'", externalDeviceId);
            return;
        }

        device.setLatitude(latitude);
        device.setLongitude(longitude);
        device.setLocationUpdatedAt(updatedAt == null ? Instant.now() : updatedAt);
        deviceRepository.save(device);
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
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String normalizeDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        return deviceId.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }
}
