package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlarmCodeUpdateWorkerServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private AlarmStreamService alarmStreamService;

    @Mock
    private DeviceTelemetryLogService deviceTelemetryLogService;

    @Test
    void processShouldUpdateAlarmCodeAndPublishEvent() {
        Device device = new Device();
        device.setExternalDeviceId("dev-01");
        Instant triggeredAt = Instant.parse("2026-03-20T11:12:13Z");
        when(deviceRepository.findByExternalDeviceId("dev-01")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlarmCodeUpdateWorkerService service = new AlarmCodeUpdateWorkerService(deviceRepository, alarmStreamService, deviceTelemetryLogService);
        service.process(new AlarmCodeUpdateRequest("dev-01", "SOS Alert", triggeredAt));

        assertEquals("SOS Alert", device.getAlarmCode());
        assertEquals(triggeredAt, device.getAlarmTriggeredAt());
        verify(deviceRepository).save(device);
        verify(alarmStreamService).publish(any());
    }

    @Test
    void processShouldClearAlarmCode() {
        Device device = new Device();
        device.setExternalDeviceId("dev-01");
        device.setAlarmCode("SOS Alert");
        device.setAlarmTriggeredAt(Instant.parse("2026-03-20T11:12:13Z"));
        when(deviceRepository.findByExternalDeviceId("dev-01")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlarmCodeUpdateWorkerService service = new AlarmCodeUpdateWorkerService(deviceRepository, alarmStreamService, deviceTelemetryLogService);
        service.process(new AlarmCodeUpdateRequest("dev-01", null, Instant.now()));

        assertNull(device.getAlarmCode());
        assertNull(device.getAlarmTriggeredAt());
        verify(deviceRepository).save(device);
        verify(alarmStreamService).publish(any());
    }


    @Test
    void processShouldIgnoreStaleSosEventsWhenDeviceWasCancelled() {
        Device device = new Device();
        device.setExternalDeviceId("dev-01");
        device.setAlarmCancelledAt(Instant.parse("2026-03-22T22:00:00Z"));

        when(deviceRepository.findByExternalDeviceId("dev-01")).thenReturn(Optional.of(device));

        AlarmCodeUpdateWorkerService service = new AlarmCodeUpdateWorkerService(deviceRepository, alarmStreamService, deviceTelemetryLogService);
        service.process(new AlarmCodeUpdateRequest("dev-01", "SOS Alert", Instant.parse("2026-03-22T22:00:00Z")));

        assertNull(device.getAlarmCode());
        verify(deviceRepository, never()).save(any(Device.class));
        verify(alarmStreamService, never()).publish(any());
    }

    @Test
    void processShouldAcceptNewerSosEventsWhenDeviceWasCancelled() {
        Device device = new Device();
        device.setExternalDeviceId("dev-01");
        device.setAlarmCancelledAt(Instant.parse("2026-03-22T22:00:00Z"));

        when(deviceRepository.findByExternalDeviceId("dev-01")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlarmCodeUpdateWorkerService service = new AlarmCodeUpdateWorkerService(deviceRepository, alarmStreamService, deviceTelemetryLogService);
        service.process(new AlarmCodeUpdateRequest("dev-01", "SOS Alert", Instant.parse("2026-03-22T22:00:01Z")));

        assertEquals("SOS Alert", device.getAlarmCode());
        verify(deviceRepository).save(device);
        verify(alarmStreamService).publish(any());
    }

    @Test
    void processShouldSkipWhenNoDeviceFound() {
        when(deviceRepository.findByExternalDeviceId("missing")).thenReturn(Optional.empty());
        when(deviceRepository.findAll()).thenReturn(List.of());
        AlarmCodeUpdateWorkerService service = new AlarmCodeUpdateWorkerService(deviceRepository, alarmStreamService, deviceTelemetryLogService);

        service.process(new AlarmCodeUpdateRequest("missing", "SOS Alert", Instant.now()));

        verify(deviceRepository, never()).save(any(Device.class));
        verify(alarmStreamService, never()).publish(any());
    }

    @Test
    void processShouldFallbackToNormalizedExternalDeviceIdMatch() {
        Device device = new Device();
        device.setExternalDeviceId("8626-670-84205114");

        when(deviceRepository.findByExternalDeviceId("862667084205114")).thenReturn(Optional.empty());
        when(deviceRepository.findAll()).thenReturn(List.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlarmCodeUpdateWorkerService service = new AlarmCodeUpdateWorkerService(deviceRepository, alarmStreamService, deviceTelemetryLogService);
        service.process(new AlarmCodeUpdateRequest("862667084205114", "SOS Alert", Instant.now()));

        assertEquals("SOS Alert", device.getAlarmCode());
        verify(deviceRepository).save(device);
        verify(alarmStreamService).publish(any());
    }
}
