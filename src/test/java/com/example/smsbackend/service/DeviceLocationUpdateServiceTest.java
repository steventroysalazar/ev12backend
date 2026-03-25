package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceLocationUpdateServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Test
    void applyNowShouldPersistCoordinatesWhenDeviceExists() {
        Device device = new Device();
        device.setExternalDeviceId("862667084205114");
        when(deviceRepository.findByExternalDeviceId("862667084205114")).thenReturn(Optional.of(device));

        DeviceLocationUpdateService service = new DeviceLocationUpdateService(deviceRepository);
        Instant updatedAt = Instant.parse("2026-03-25T12:48:17.268Z");

        service.applyNow("862667084205114", 15.1468038, 120.5463361, updatedAt);

        assertEquals(15.1468038, device.getLatitude());
        assertEquals(120.5463361, device.getLongitude());
        assertEquals(updatedAt, device.getLocationUpdatedAt());
        verify(deviceRepository).save(device);
    }

    @Test
    void applyNowShouldSkipWhenCoordinatesAreMissing() {
        DeviceLocationUpdateService service = new DeviceLocationUpdateService(deviceRepository);

        service.applyNow("862667084205114", null, 120.5463361, Instant.now());

        verify(deviceRepository, never()).save(org.mockito.ArgumentMatchers.any(Device.class));
    }
}
