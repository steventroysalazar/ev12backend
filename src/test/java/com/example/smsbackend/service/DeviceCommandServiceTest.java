package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.smsbackend.dto.DeviceContactSettings;
import com.example.smsbackend.dto.SendConfigRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeviceCommandServiceTest {

    private final DeviceCommandService service = new DeviceCommandService();

    @Test
    void buildCommands_supportsMultipleContactsUpToTen() {
        List<DeviceContactSettings> contacts = java.util.stream.IntStream.rangeClosed(1, 11)
            .mapToObj(i -> new DeviceContactSettings(i, true, false, "90000000" + i, "C" + i))
            .toList();

        SendConfigRequest request = new SendConfigRequest(
            1L,
            null,
            null,
            null,
            null,
            contacts,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        List<String> commands = service.buildCommands(request);

        assertEquals(10, commands.size());
        assertEquals("A1,1,0,900000001,C1", commands.get(0));
        assertEquals("A10,1,0,9000000010,C10", commands.get(9));
    }

    @Test
    void buildCommands_keepsLegacySingleContactPayloadCompatible() {
        SendConfigRequest request = new SendConfigRequest(
            1L,
            null,
            null,
            null,
            null,
            null,
            "123456789",
            3,
            true,
            true,
            "Emma",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        List<String> commands = service.buildCommands(request);

        assertEquals(1, commands.size());
        assertTrue(commands.contains("A3,1,1,123456789,Emma"));
    }
}
