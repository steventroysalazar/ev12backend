package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.example.smsbackend.entity.Ev12WebhookEvent;
import com.example.smsbackend.repository.Ev12WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

class Ev12WebhookServiceTest {

    @Mock
    private Ev12WebhookEventRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void ingestShouldStoreRawPayload() throws Exception {
        byte[] rawPayload = "{\"Configuration Command\":{\"IMEI\":\"862667084205114\"}}".getBytes();
        when(repository.save(any(Ev12WebhookEvent.class))).thenAnswer(invocation -> {
            Ev12WebhookEvent event = invocation.getArgument(0);
            event.setReceivedAt(Instant.now());
            return event;
        });

        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties(null), objectMapper);
        Ev12WebhookEventResponse response = service.ingest(
            rawPayload,
            "application/json",
            null,
            Map.of("X-Test", "header-value")
        );

        JsonNode payloadJson = objectMapper.readTree(response.payloadJson());
        assertEquals(new String(rawPayload), payloadJson.get("rawBody").asText());
        assertEquals("application/json", payloadJson.get("contentType").asText());
        assertEquals("header-value", payloadJson.get("rawHeaders").get("X-Test").asText());
    }


    @Test
    void ingestShouldBase64EncodeBinaryPayload() throws Exception {
        byte[] rawPayload = new byte[]{0x01, 0x02, 0x03};
        when(repository.save(any(Ev12WebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties(null), objectMapper);
        Ev12WebhookEventResponse response = service.ingest(rawPayload, "application/octet-stream", null, Map.of());

        JsonNode payloadJson = objectMapper.readTree(response.payloadJson());
        assertEquals("base64:AQID", payloadJson.get("rawBody").asText());
    }

    @Test
    void ingestShouldRequireValidTokenWhenConfigured() {
        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties("secret"), objectMapper);

        assertThrows(ResponseStatusException.class, () -> service.ingest("{}".getBytes(), "application/json", "wrong", Map.of()));
    }

    @Test
    void ingestShouldAcceptEmptyPayload() throws Exception {
        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties(null), objectMapper);

        Ev12WebhookEventResponse response = service.ingest(new byte[0], "application/octet-stream", null, Map.of());

        JsonNode payloadJson = objectMapper.readTree(response.payloadJson());
        assertEquals("", payloadJson.get("rawBody").asText());
    }
}
