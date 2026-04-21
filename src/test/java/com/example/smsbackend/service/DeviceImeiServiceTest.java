package com.example.smsbackend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smsbackend.dto.GatewayReplyMessage;
import com.example.smsbackend.dto.SendMessageRequest;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceImeiServiceTest {

    @Mock
    private GatewayClientService gatewayClientService;

    @Mock
    private DeviceRepository deviceRepository;

    private DeviceImeiService service;

    @BeforeEach
    void setUp() {
        service = new DeviceImeiService(gatewayClientService, deviceRepository);
    }

    @Test
    void requestImei_sendsUppercaseVQuestionMark() {
        Device device = new Device();
        device.setPhoneNumber("+15551234567");

        service.requestImei(device, null);

        verify(gatewayClientService).sendMessage(
            eq(new SendMessageRequest("+15551234567", "V?", null)),
            eq(null)
        );
    }

    @Test
    void processReplies_updatesExternalDeviceIdFromImeiMessage() {
        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 10L);
        device.setPhoneNumber("+15551234567");

        when(deviceRepository.findByPhoneNumber("+15551234567")).thenReturn(Optional.of(device));

        service.processReplies(List.of(new GatewayReplyMessage(
            101L,
            "+15551234567",
            "Status OK IMEI:123456789012345",
            1710000000000L
        )));

        verify(deviceRepository).save(any(Device.class));
    }


    @Test
    void processReplies_matchesPhoneWithDifferentFormattingAndUpdatesNewestPendingDevice() {
        Device older = new Device();
        ReflectionTestUtils.setField(older, "id", 2L);
        older.setPhoneNumber("+1 (555) 123-4567");
        older.setExternalDeviceId("already-set");

        Device newestPending = new Device();
        ReflectionTestUtils.setField(newestPending, "id", 9L);
        newestPending.setPhoneNumber("15551234567");

        when(deviceRepository.findByPhoneNumber("+15551234567")).thenReturn(Optional.empty());
        when(deviceRepository.findAll()).thenReturn(List.of(older, newestPending));

        service.processReplies(List.of(new GatewayReplyMessage(
            303L,
            "+15551234567",
            "IMEI:862667084205114\nGSM signal quality: 19",
            1710000010000L
        )));

        verify(deviceRepository).save(eq(newestPending));
    }

    @Test
    void processReplies_ignoresMessagesWithoutImei() {
        service.processReplies(List.of(new GatewayReplyMessage(
            202L,
            "+15550000000",
            "Battery: 90%",
            1710000005000L
        )));

        verify(deviceRepository, never()).save(any(Device.class));
    }
}
