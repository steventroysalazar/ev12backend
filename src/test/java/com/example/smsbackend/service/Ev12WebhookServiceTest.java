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
        String rawPayload = "{\"Configuration Command\":{\"IMEI\":\"862667084205114\"}}";
        when(repository.save(any(Ev12WebhookEvent.class))).thenAnswer(invocation -> {
            Ev12WebhookEvent event = invocation.getArgument(0);
            event.setReceivedAt(Instant.now());
            return event;
        });

        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties(null));
        Ev12WebhookEventResponse response = service.ingest(rawPayload, null);

        assertEquals(rawPayload, response.payloadJson());
    }

    @Test
    void ingestShouldRequireValidTokenWhenConfigured() {
        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties("secret"));

        assertThrows(ResponseStatusException.class, () -> service.ingest("{}", "wrong"));
    }

    @Test
    void ingestShouldRejectEmptyPayload() {
        Ev12WebhookService service = new Ev12WebhookService(repository, new WebhookProperties(null));

        assertThrows(IllegalArgumentException.class, () -> service.ingest("   ", null));
    }
}
