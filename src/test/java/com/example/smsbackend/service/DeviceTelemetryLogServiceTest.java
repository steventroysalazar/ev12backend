package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.smsbackend.repository.DeviceAlarmLogRepository;
import com.example.smsbackend.repository.DeviceLocationBreadcrumbRepository;
import com.example.smsbackend.repository.DeviceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTelemetryLogServiceTest {

    @Mock
    private DeviceAlarmLogRepository deviceAlarmLogRepository;

    @Mock
    private DeviceLocationBreadcrumbRepository deviceLocationBreadcrumbRepository;

    @Mock
    private DeviceRepository deviceRepository;

    private DeviceTelemetryLogService service;

    @BeforeEach
    void setUp() {
        service = new DeviceTelemetryLogService(
            deviceAlarmLogRepository,
            deviceLocationBreadcrumbRepository,
            deviceRepository
        );
    }

    @Test
    void listActiveAlertsLookup_returnsDistinctCodes() {
        when(deviceRepository.findDistinctActiveAlarmCodes())
            .thenReturn(List.of("Fall-Down Alert", "SOS Alert"));

        var response = service.listActiveAlertsLookup();

        assertEquals(List.of("Fall-Down Alert", "SOS Alert"), response);
    }

    @Test
    void listAlertLogFiltersLookup_returnsAlarmActionAndSourceLists() {
        when(deviceAlarmLogRepository.findDistinctAlarmCodes())
            .thenReturn(List.of("Fall-Down Alert", "SOS Alert"));
        when(deviceAlarmLogRepository.findDistinctActions())
            .thenReturn(List.of("ALARM_CANCELLED", "ALARM_TRIGGERED"));
        when(deviceAlarmLogRepository.findDistinctSources())
            .thenReturn(List.of("MANUAL", "WEBHOOK"));

        var response = service.listAlertLogFiltersLookup();

        assertEquals(List.of("Fall-Down Alert", "SOS Alert"), response.alarmCodes());
        assertEquals(List.of("ALARM_CANCELLED", "ALARM_TRIGGERED"), response.actions());
        assertEquals(List.of("MANUAL", "WEBHOOK"), response.sources());
    }
}
