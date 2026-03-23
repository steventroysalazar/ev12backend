package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.example.smsbackend.entity.Ev12WebhookEvent;
import com.example.smsbackend.repository.Ev12WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class Ev12WebhookServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AlarmCodeUpdateWorkerService alarmCodeUpdateWorkerService;

    @Mock
    private Ev12WebhookEventRepository ev12WebhookEventRepository;

    private void mockApplyNowSuccess() {
        when(alarmCodeUpdateWorkerService.applyNow(any())).thenReturn(
            AlarmCodeUpdateResult.applied(1L, "862667084205114", "SOS Alert")
        );
    }

    private void mockSaveWebhookEvent() {
        when(ev12WebhookEventRepository.save(any(Ev12WebhookEvent.class))).thenAnswer(invocation -> {
            Ev12WebhookEvent event = invocation.getArgument(0);
            if (event.getReceivedAt() == null) {
                event.setReceivedAt(Instant.now());
            }
            setEntityId(event, 1L);
            return event;
        });
    }

    private void setEntityId(Ev12WebhookEvent event, Long id) {
        try {
            java.lang.reflect.Field field = Ev12WebhookEvent.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(event, id);
        } catch (Exception ignored) {
            // no-op in unit tests
        }
    }

    private Ev12WebhookService createService(WebhookProperties properties) {
        return new Ev12WebhookService(properties, objectMapper, alarmCodeUpdateWorkerService, ev12WebhookEventRepository);
    }

    @Test
    void ingestShouldStoreRawPayloadAndPersistEvent() throws Exception {
        mockApplyNowSuccess();
        mockSaveWebhookEvent();
        byte[] rawPayload = "{\"Configuration Command\":{\"IMEI\":\"862667084205114\"}}".getBytes();

        Ev12WebhookService service = createService(new WebhookProperties(null));
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
        assertFalse(response.alarmAttempts().isEmpty());
        verify(ev12WebhookEventRepository).save(any(Ev12WebhookEvent.class));
    }

    @Test
    void ingestShouldBase64EncodeBinaryPayload() throws Exception {
        mockSaveWebhookEvent();
        byte[] rawPayload = new byte[]{0x01, 0x02, 0x03};

        Ev12WebhookService service = createService(new WebhookProperties(null));
        Ev12WebhookEventResponse response = service.ingest(rawPayload, "application/octet-stream", null, Map.of());

        JsonNode payloadJson = objectMapper.readTree(response.payloadJson());
        assertEquals("base64:AQID", payloadJson.get("rawBody").asText());
    }

    @Test
    void ingestShouldRequireValidTokenWhenConfigured() {
        Ev12WebhookService service = createService(new WebhookProperties("secret"));

        assertThrows(ResponseStatusException.class, () -> service.ingest("{}".getBytes(), "application/json", "wrong", Map.of()));
    }

    @Test
    void ingestShouldExposeAlarmAttemptDebugDataWhenUpdateIsApplied() {
        mockApplyNowSuccess();
        mockSaveWebhookEvent();
        Ev12WebhookService service = createService(new WebhookProperties(null));

        Ev12WebhookEventResponse response = service.ingest(
            "{\"deviceId\":\"862667084205114\",\"data\":{\"Alarm Code\":[\"SOS Alert\"]}}".getBytes(),
            "application/json",
            null,
            Map.of()
        );

        assertEquals(1, response.alarmAttempts().size());
        assertEquals("applied", response.alarmAttempts().get(0).action());
        assertEquals("862667084205114", response.alarmAttempts().get(0).externalDeviceId());
        assertEquals("SOS Alert", response.alarmAttempts().get(0).alarmCode());
    }

    @Test
    void recentEventsShouldReadPersistedEventsFromDatabase() {
        Ev12WebhookService service = createService(new WebhookProperties(null));
        Ev12WebhookEvent persisted = new Ev12WebhookEvent();
        persisted.setReceivedAt(Instant.now());
        persisted.setPayloadJson("{}");
        persisted.setAlarmAttemptsJson("[]");
        setEntityId(persisted, 9L);

        when(ev12WebhookEventRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "receivedAt"))))
            .thenReturn(new PageImpl<>(List.of(persisted, persisted)));

        List<Ev12WebhookEventResponse> events = service.recentEvents(2, null);

        assertEquals(2, events.size());
        assertEquals(9L, events.get(0).id());
    }

    @Test
    void clearEventsShouldDeletePersistedEvents() {
        when(ev12WebhookEventRepository.count()).thenReturn(2L);
        Ev12WebhookService service = createService(new WebhookProperties(null));

        int deleted = service.clearEvents(null);

        assertEquals(2, deleted);
        verify(ev12WebhookEventRepository).deleteAll();
    }

    @Test
    void clearEventsShouldRequireTokenWhenConfigured() {
        Ev12WebhookService service = createService(new WebhookProperties("secret"));

        assertThrows(ResponseStatusException.class, () -> service.clearEvents("wrong"));
    }

    @Test
    void ingestShouldCallApplyNowForParsedAlarmCandidates() {
        mockApplyNowSuccess();
        mockSaveWebhookEvent();
        Ev12WebhookService service = createService(new WebhookProperties(null));

        service.ingest(
            ("[" +
                "{\"deviceId\":\"862667084205114\",\"timestamp\":1774296609413,\"data\":{\"Alarm Code\":[\"SOS Alert\"]}}," +
                "{\"deviceId\":\"862667084205114\",\"timestamp\":1774297800000,\"data\":{\"Alarm Code\":[\"SOS Alert\",\"SOS Ending\"]}}" +
            "]")
                .getBytes(),
            "application/json",
            null,
            Map.of()
        );

        verify(alarmCodeUpdateWorkerService, times(2)).applyNow(argThat(request ->
            "862667084205114".equals(request.externalDeviceId())
                && ("SOS Alert".equals(request.alarmCode()) || "SOS Ending".equals(request.alarmCode()))
        ));
    }
}
