package com.example.smsbackend.service;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Ev12WebhookService {

    private static final String SOS_ALERT = "SOS Alert";
    private static final String SOS_ENDING = "SOS Ending";
    private static final String FALL_DOWN_ALERT = "Fall-Down Alert";

    private final WebhookProperties webhookProperties;
    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;
    private final AtomicLong eventIdSequence = new AtomicLong(1);
    private final Deque<Ev12WebhookEventResponse> recentEvents = new ArrayDeque<>();

    public Ev12WebhookService(
        WebhookProperties webhookProperties,
        ObjectMapper objectMapper,
        DeviceRepository deviceRepository
    ) {
        this.webhookProperties = webhookProperties;
        this.objectMapper = objectMapper;
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public synchronized Ev12WebhookEventResponse ingest(
        byte[] rawPayload,
        String contentType,
        String providedToken,
        Map<String, String> rawHeaders
    ) {
        validateToken(providedToken);
        updateDeviceAlarmCode(rawPayload);

        Ev12WebhookEventResponse event = new Ev12WebhookEventResponse(
            eventIdSequence.getAndIncrement(),
            Instant.now(),
            serializePayload(rawPayload, contentType, rawHeaders)
        );

        recentEvents.addFirst(event);
        return event;
    }

    public synchronized List<Ev12WebhookEventResponse> recentEvents(Integer limit, String providedToken) {
        validateToken(providedToken);

        List<Ev12WebhookEventResponse> events = new ArrayList<>(recentEvents);
        if (limit == null) {
            return events;
        }

        int normalizedLimit = Math.max(1, limit);
        return events.stream()
            .limit(normalizedLimit)
            .toList();
    }

    public synchronized int clearEvents(String providedToken) {
        validateToken(providedToken);

        int deletedCount = recentEvents.size();
        recentEvents.clear();
        return deletedCount;
    }

    private void updateDeviceAlarmCode(byte[] rawPayload) {
        if (rawPayload == null || rawPayload.length == 0) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(new String(rawPayload, StandardCharsets.UTF_8));
            String externalDeviceId = root.path("deviceId").asText(null);
            if (!StringUtils.hasText(externalDeviceId)) {
                return;
            }

            JsonNode alarmCodeNode = root.path("data").path("Alarm Code");
            if (!alarmCodeNode.isArray()) {
                return;
            }

            String nextAlarmCode = deriveAlarmCode(alarmCodeNode);
            if (nextAlarmCode == null && !containsCode(alarmCodeNode, SOS_ENDING)) {
                return;
            }

            Device device = deviceRepository.findByExternalDeviceId(externalDeviceId.trim())
                .orElse(null);
            if (device == null) {
                return;
            }

            device.setAlarmCode(nextAlarmCode);
            deviceRepository.save(device);
        } catch (Exception ignored) {
            // Ignore malformed webhook payloads. Raw payload is still stored for diagnostics.
        }
    }

    private String deriveAlarmCode(JsonNode alarmCodeNode) {
        if (containsCode(alarmCodeNode, SOS_ENDING)) {
            return null;
        }
        if (containsCode(alarmCodeNode, SOS_ALERT)) {
            return SOS_ALERT;
        }
        if (containsCode(alarmCodeNode, FALL_DOWN_ALERT)) {
            return FALL_DOWN_ALERT;
        }
        return null;
    }

    private boolean containsCode(JsonNode alarmCodeNode, String code) {
        for (JsonNode item : alarmCodeNode) {
            if (code.equalsIgnoreCase(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private String serializePayload(byte[] rawPayload, String contentType, Map<String, String> rawHeaders) {
        Map<String, Object> payloadEnvelope = new LinkedHashMap<>();
        payloadEnvelope.put("rawHeaders", rawHeaders == null ? Map.of() : rawHeaders);
        payloadEnvelope.put("contentType", contentType);
        payloadEnvelope.put("rawBody", serializeRawBody(rawPayload, contentType));

        try {
            return objectMapper.writeValueAsString(payloadEnvelope);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize webhook payload", exception);
        }
    }

    private String serializeRawBody(byte[] rawPayload, String contentType) {
        if (rawPayload == null || rawPayload.length == 0) {
            return "";
        }

        if (isTextPayload(contentType)) {
            return new String(rawPayload, StandardCharsets.UTF_8);
        }

        return "base64:" + Base64.getEncoder().encodeToString(rawPayload);
    }

    private boolean isTextPayload(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return true;
        }

        String normalized = contentType.toLowerCase();
        return normalized.contains("json")
            || normalized.contains("text")
            || normalized.contains("xml")
            || normalized.contains("x-www-form-urlencoded");
    }

    private void validateToken(String providedToken) {
        if (!StringUtils.hasText(webhookProperties.ev12Token())) {
            return;
        }
        if (!StringUtils.hasText(providedToken) || !webhookProperties.ev12Token().equals(providedToken.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook token");
        }
    }
}
