package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class Ev12WebhookServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AlarmCodeUpdateWorkerService alarmCodeUpdateWorkerService;

    @Test
    void ingestShouldStoreRawPayload() throws Exception {
        byte[] rawPayload = "{\"Configuration Command\":{\"IMEI\":\"862667084205114\"}}".getBytes();

        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
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

        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
        Ev12WebhookEventResponse response = service.ingest(rawPayload, "application/octet-stream", null, Map.of());

        JsonNode payloadJson = objectMapper.readTree(response.payloadJson());
        assertEquals("base64:AQID", payloadJson.get("rawBody").asText());
    }

    @Test
    void ingestShouldRequireValidTokenWhenConfigured() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties("secret"), objectMapper, alarmCodeUpdateWorkerService);

        assertThrows(ResponseStatusException.class, () -> service.ingest("{}".getBytes(), "application/json", "wrong", Map.of()));
    }

    @Test
    void ingestShouldAcceptEmptyPayload() throws Exception {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);

        Ev12WebhookEventResponse response = service.ingest(new byte[0], "application/octet-stream", null, Map.of());

        JsonNode payloadJson = objectMapper.readTree(response.payloadJson());
        assertEquals("", payloadJson.get("rawBody").asText());
    }

    @Test
    void recentEventsShouldApplyRequestedLimitWithoutHardCap() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);

        service.ingest("{\"seq\":1}".getBytes(), "application/json", null, Map.of());
        service.ingest("{\"seq\":2}".getBytes(), "application/json", null, Map.of());
        service.ingest("{\"seq\":3}".getBytes(), "application/json", null, Map.of());
        service.ingest("{\"seq\":4}".getBytes(), "application/json", null, Map.of());

        List<Ev12WebhookEventResponse> events = service.recentEvents(2, null);

        assertEquals(2, events.size());
    }

    @Test
    void recentEventsShouldReturnAllEventsWithoutCap() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);

        service.ingest("{\"seq\":1}".getBytes(), "application/json", null, Map.of());
        service.ingest("{\"seq\":2}".getBytes(), "application/json", null, Map.of());
        service.ingest("{\"seq\":3}".getBytes(), "application/json", null, Map.of());
        service.ingest("{\"seq\":4}".getBytes(), "application/json", null, Map.of());

        List<Ev12WebhookEventResponse> events = service.recentEvents(null, null);

        assertEquals(4, events.size());
    }

    @Test
    void clearEventsShouldRemoveAllStoredEvents() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);

        service.ingest("{\"seq\":1}".getBytes(), "application/json", null, Map.of());
        service.ingest("{\"seq\":2}".getBytes(), "application/json", null, Map.of());

        int deleted = service.clearEvents(null);

        assertEquals(2, deleted);
        assertEquals(0, service.recentEvents(null, null).size());
    }

    @Test
    void clearEventsShouldRequireTokenWhenConfigured() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties("secret"), objectMapper, alarmCodeUpdateWorkerService);

        assertThrows(ResponseStatusException.class, () -> service.clearEvents("wrong"));
    }

    @Test
    void ingestShouldSetDeviceAlarmCodeForSosAlert() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
        service.ingest(
            "{\"deviceId\":\"862667084205114\",\"data\":{\"Alarm Code\":[\"SOS Alert\"]}}".getBytes(),
            "application/json",
            null,
            Map.of()
        );

        verify(alarmCodeUpdateWorkerService).enqueue(argThat(request ->
            "862667084205114".equals(request.externalDeviceId()) && "SOS Alert".equals(request.alarmCode())
        ));
    }

    @Test
    void ingestShouldNormalizeSosEndingToSosAlert() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
        service.ingest(
            "{\"deviceId\":\"862667084205114\",\"data\":{\"Alarm Code\":[\"SOS Alert\",\"SOS Ending\"]}}".getBytes(),
            "application/json",
            null,
            Map.of()
        );

        verify(alarmCodeUpdateWorkerService).enqueue(argThat(request ->
            "862667084205114".equals(request.externalDeviceId()) && "SOS Alert".equals(request.alarmCode())
        ));
    }

    @Test
    void ingestShouldSetDeviceAlarmCodeForFallDownAlert() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
        service.ingest(
            "{\"deviceId\":\"862667084205114\",\"data\":{\"Alarm Code\":[\"Fall-Down Alert\"]}}".getBytes(),
            "application/json",
            null,
            Map.of()
        );

        verify(alarmCodeUpdateWorkerService).enqueue(argThat(request ->
            "862667084205114".equals(request.externalDeviceId()) && "Fall-Down Alert".equals(request.alarmCode())
        ));
    }

    @Test
    void ingestShouldSetDeviceAlarmCodeWhenAlarmCodeIsTopLevel() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
        service.ingest(
            "{\"deviceId\":\"862667084205114\",\"Alarm Code\":[\"SOS Alert\"]}".getBytes(),
            "application/json",
            null,
            Map.of()
        );

        verify(alarmCodeUpdateWorkerService).enqueue(argThat(request ->
            "862667084205114".equals(request.externalDeviceId()) && "SOS Alert".equals(request.alarmCode())
        ));
    }

    @Test
    void ingestShouldSetDeviceAlarmCodeWhenAlarmCodeIsStringAndUsePayloadTimestamp() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
        service.ingest(
            "{\"deviceId\":\"862667084205114\",\"timestamp\":1774215008810,\"data\":{\"alarmCode\":\"SOS ending\"}}".getBytes(),
            "application/json",
            null,
            Map.of()
        );

        verify(alarmCodeUpdateWorkerService).enqueue(argThat(request ->
            "862667084205114".equals(request.externalDeviceId())
                && "SOS Alert".equals(request.alarmCode())
                && request.updatedAt() != null
                && request.updatedAt().toEpochMilli() == 1774215008810L
        ));
    }

    @Test
    void ingestShouldSetDeviceAlarmCodeWhenDataHasUpperCaseKey() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
        service.ingest(
            "{\"deviceId\":\"862667084205114\",\"Data\":{\"Alarm Code\":[\"SOS Alert\"]}}".getBytes(),
            "application/json",
            null,
            Map.of()
        );

        verify(alarmCodeUpdateWorkerService).enqueue(argThat(request ->
            "862667084205114".equals(request.externalDeviceId()) && "SOS Alert".equals(request.alarmCode())
        ));
    }

    @Test
    void ingestShouldSetDeviceAlarmCodeWhenWebhookContainsResponseObject() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
        service.ingest(
            "{\"response\":{\"deviceId\":\"862667084205114\",\"data\":{\"Alarm Code\":[\"SOS Alert\"]}}}".getBytes(),
            "application/json",
            null,
            Map.of()
        );

        verify(alarmCodeUpdateWorkerService).enqueue(argThat(request ->
            "862667084205114".equals(request.externalDeviceId()) && "SOS Alert".equals(request.alarmCode())
        ));
    }

    @Test
    void ingestShouldSetDeviceAlarmCodeWhenWebhookContainsSerializedResponsePayload() {
        Ev12WebhookService service = new Ev12WebhookService(new WebhookProperties(null), objectMapper, alarmCodeUpdateWorkerService);
        service.ingest(
            ("{\"data\":{\"response\":\"{\\\"deviceId\\\":\\\"862667084205114\\\",\\\"data\\\":{\\\"Alarm Code\\\":[\\\"SOS Alert\\\"]}}\"}}")
                .getBytes(),
            "application/json",
            null,
            Map.of()
        );

        verify(alarmCodeUpdateWorkerService).enqueue(argThat(request ->
            "862667084205114".equals(request.externalDeviceId()) && "SOS Alert".equals(request.alarmCode())
        ));
    }
}
