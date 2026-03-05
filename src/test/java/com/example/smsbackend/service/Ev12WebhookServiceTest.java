package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.example.smsbackend.entity.Ev12WebhookEvent;
import com.example.smsbackend.repository.Ev12WebhookEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

class Ev12WebhookServiceTest {

    @Mock
    private Ev12WebhookEventRepository repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void ingestShouldStoreRawPayload() {
        byte[] rawPayload = "{\"Configuration Command\":{\"IMEI\":\"862667084205114\"}}".getBytes();
        when(repository.save(any(Ev12WebhookEvent.class))).thenAnswer(invocation -> {
            Ev12WebhookEvent event = invocation.getArgument(0);
            event.setReceivedAt(Instant.now());
            return event;
        });

        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties(null));
        Ev12WebhookEventResponse response = service.ingest(rawPayload, "application/json", null);

        assertEquals(new String(rawPayload), response.payloadJson());
    }


    @Test
    void ingestShouldBase64EncodeBinaryPayload() {
        byte[] rawPayload = new byte[]{0x01, 0x02, 0x03};
        when(repository.save(any(Ev12WebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties(null));
        Ev12WebhookEventResponse response = service.ingest(rawPayload, "application/octet-stream", null);

        assertEquals("base64:AQID", response.payloadJson());
    }

    @Test
    void ingestShouldRequireValidTokenWhenConfigured() {
        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties("secret"));

        assertThrows(ResponseStatusException.class, () -> service.ingest("{}".getBytes(), "application/json", "wrong"));
    }

    @Test
    void ingestShouldAcceptEmptyPayload() {
        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties(null));

        Ev12WebhookEventResponse response = service.ingest(new byte[0], "application/octet-stream", null);

        assertEquals("", response.payloadJson());
    }
}
