package com.example.smsbackend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.smsbackend.dto.SendConfigRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JacksonConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JacksonConfig().flexibleBooleanModule());

    @Test
    void shouldDeserializeBooleanLikeValuesFromStrings() throws Exception {
        String payload = """
            {
              "deviceId": 123,
              "contactSmsEnabled": "1",
              "contactCallEnabled": "0",
              "wifiEnabled": "true",
              "bluetoothEnabled": "false",
              "beepEnabled": 1,
              "vibrationEnabled": 0
            }
            """;

        SendConfigRequest request = objectMapper.readValue(payload, SendConfigRequest.class);

        assertTrue(request.contactSmsEnabled());
        assertFalse(request.contactCallEnabled());
        assertTrue(request.wifiEnabled());
        assertFalse(request.bluetoothEnabled());
        assertTrue(request.beepEnabled());
        assertFalse(request.vibrationEnabled());
    }
}

