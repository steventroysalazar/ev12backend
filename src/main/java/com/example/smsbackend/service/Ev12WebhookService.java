package com.example.smsbackend.service;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final String FALL_DOWN_ALERT = "Fall-Down Alert";

    private final WebhookProperties webhookProperties;
    private final ObjectMapper objectMapper;
    private final AlarmCodeUpdateWorkerService alarmCodeUpdateWorkerService;
    private final AtomicLong eventIdSequence = new AtomicLong(1);
    private final Deque<Ev12WebhookEventResponse> recentEvents = new ArrayDeque<>();

    public Ev12WebhookService(
        WebhookProperties webhookProperties,
        ObjectMapper objectMapper,
        AlarmCodeUpdateWorkerService alarmCodeUpdateWorkerService
    ) {
        this.webhookProperties = webhookProperties;
        this.objectMapper = objectMapper;
        this.alarmCodeUpdateWorkerService = alarmCodeUpdateWorkerService;
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
            String externalDeviceId = extractExternalDeviceId(root);
            if (!StringUtils.hasText(externalDeviceId)) {
                return;
            }

            JsonNode alarmCodeNode = extractAlarmCodeNode(root);
            if (alarmCodeNode.isMissingNode() || alarmCodeNode.isNull()) {
                return;
            }

            String nextAlarmCode = deriveAlarmCode(alarmCodeNode);
            if (nextAlarmCode == null) {
                return;
            }

            alarmCodeUpdateWorkerService.enqueue(new AlarmCodeUpdateRequest(
                externalDeviceId.trim(),
                nextAlarmCode,
                extractEventTimestamp(root)
            ));
        } catch (Exception ignored) {
            // Ignore malformed webhook payloads. Raw payload is still stored for diagnostics.
        }
    }

    private String extractExternalDeviceId(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return null;
        }

        String direct = root.path("deviceId").asText(null);
        if (StringUtils.hasText(direct)) {
            return direct;
        }

        String nested = root.path("device").path("deviceId").asText(null);
        if (StringUtils.hasText(nested)) {
            return nested;
        }

        return root.path("IMEI").asText(null);
    }

    private JsonNode extractAlarmCodeNode(JsonNode root) {
        JsonNode topLevel = firstPresentNode(root, "Alarm Code", "alarmCode", "alarm_code");
        if (!topLevel.isMissingNode() && !topLevel.isNull()) {
            return topLevel;
        }

        JsonNode data = firstPresentNode(root, "data", "Data");
        if (!data.isMissingNode() && !data.isNull()) {
            JsonNode nested = firstPresentNode(data, "Alarm Code", "alarmCode", "alarm_code");
            if (!nested.isMissingNode() && !nested.isNull()) {
                return nested;
            }
        }

        return JsonNodeFactory.instance.missingNode();
    }

    private JsonNode firstPresentNode(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.path(fieldName);
            if (!candidate.isMissingNode()) {
                return candidate;
            }
        }
        return JsonNodeFactory.instance.missingNode();
    }

    private String deriveAlarmCode(JsonNode alarmCodeNode) {
        if (containsSosCode(alarmCodeNode)) {
            return SOS_ALERT;
        }
        if (containsCode(alarmCodeNode, FALL_DOWN_ALERT)) {
            return FALL_DOWN_ALERT;
        }
        return null;
    }

    private boolean containsSosCode(JsonNode alarmCodeNode) {
        if (alarmCodeNode.isArray()) {
            for (JsonNode item : alarmCodeNode) {
                if (isSosLike(item.asText())) {
                    return true;
                }
            }
            return false;
        }

        return isSosLike(alarmCodeNode.asText());
    }

    private boolean isSosLike(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains("sos");
    }

    private boolean containsCode(JsonNode alarmCodeNode, String code) {
        if (alarmCodeNode.isArray()) {
            for (JsonNode item : alarmCodeNode) {
                if (code.equalsIgnoreCase(item.asText())) {
                    return true;
                }
            }
            return false;
        }
        return code.equalsIgnoreCase(alarmCodeNode.asText());
    }

    private Instant extractEventTimestamp(JsonNode root) {
        Instant direct = parseTimestamp(root.path("timestamp"));
        if (direct != null) {
            return direct;
        }

        Instant eventTime = parseTimestamp(root.path("eventTime"));
        if (eventTime != null) {
            return eventTime;
        }

        Instant nested = parseTimestamp(root.path("data").path("timestamp"));
        if (nested != null) {
            return nested;
        }

        return Instant.now();
    }

    private Instant parseTimestamp(JsonNode timestampNode) {
        if (timestampNode == null || timestampNode.isMissingNode() || timestampNode.isNull()) {
            return null;
        }

        if (timestampNode.isNumber()) {
            long raw = timestampNode.asLong();
            return raw >= 1_000_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        }

        if (timestampNode.isTextual()) {
            String text = timestampNode.asText();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            try {
                return Instant.parse(text.trim());
            } catch (DateTimeParseException ignored) {
                try {
                    long raw = Long.parseLong(text.trim());
                    return raw >= 1_000_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
                } catch (NumberFormatException ignoredNumber) {
                    return null;
                }
            }
        }

        return null;
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
