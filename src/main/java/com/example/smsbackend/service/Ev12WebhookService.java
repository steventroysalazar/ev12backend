package com.example.smsbackend.service;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.example.smsbackend.dto.WebhookAlarmAttemptResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Ev12WebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(Ev12WebhookService.class);

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
        List<WebhookAlarmAttemptResponse> alarmAttempts = updateDeviceAlarmCode(rawPayload);

        Ev12WebhookEventResponse event = new Ev12WebhookEventResponse(
            eventIdSequence.getAndIncrement(),
            Instant.now(),
            serializePayload(rawPayload, contentType, rawHeaders),
            alarmAttempts
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

    private List<WebhookAlarmAttemptResponse> updateDeviceAlarmCode(byte[] rawPayload) {
        List<WebhookAlarmAttemptResponse> alarmAttempts = new ArrayList<>();
        if (rawPayload == null || rawPayload.length == 0) {
            alarmAttempts.add(new WebhookAlarmAttemptResponse(
                0,
                null,
                null,
                null,
                "ignored",
                "empty payload"
            ));
            return alarmAttempts;
        }

        try {
            JsonNode root = objectMapper.readTree(new String(rawPayload, StandardCharsets.UTF_8));
            int candidateIndex = 0;
            for (JsonNode candidatePayload : candidatePayloads(root)) {
                String externalDeviceId = extractExternalDeviceId(candidatePayload);
                if (!StringUtils.hasText(externalDeviceId)) {
                    alarmAttempts.add(new WebhookAlarmAttemptResponse(
                        candidateIndex++,
                        null,
                        null,
                        null,
                        "ignored",
                        "missing deviceId"
                    ));
                    continue;
                }

                JsonNode alarmCodeNode = extractAlarmCodeNode(candidatePayload);
                if (alarmCodeNode.isMissingNode() || alarmCodeNode.isNull()) {
                    alarmAttempts.add(new WebhookAlarmAttemptResponse(
                        candidateIndex++,
                        externalDeviceId.trim(),
                        null,
                        null,
                        "ignored",
                        "missing alarm code"
                    ));
                    continue;
                }

                String nextAlarmCode = deriveAlarmCode(alarmCodeNode);
                if (nextAlarmCode == null) {
                    alarmAttempts.add(new WebhookAlarmAttemptResponse(
                        candidateIndex++,
                        externalDeviceId.trim(),
                        null,
                        null,
                        "ignored",
                        "alarm code does not contain sos/fall"
                    ));
                    continue;
                }

                Instant eventTimestamp = extractEventTimestamp(candidatePayload);
                alarmCodeUpdateWorkerService.enqueue(new AlarmCodeUpdateRequest(
                    externalDeviceId.trim(),
                    nextAlarmCode,
                    eventTimestamp
                ));
                WebhookAlarmAttemptResponse attempt = new WebhookAlarmAttemptResponse(
                    candidateIndex++,
                    externalDeviceId.trim(),
                    nextAlarmCode,
                    eventTimestamp,
                    "queued",
                    "alarm update enqueued"
                );
                alarmAttempts.add(attempt);
                LOGGER.info(
                    "EV12 webhook queued alarm update: deviceId='{}', alarmCode='{}', eventTimestamp='{}'",
                    attempt.externalDeviceId(),
                    attempt.alarmCode(),
                    attempt.eventTimestamp()
                );
            }
        } catch (Exception ignored) {
            // Ignore malformed webhook payloads. Raw payload is still stored for diagnostics.
            alarmAttempts.add(new WebhookAlarmAttemptResponse(
                0,
                null,
                null,
                null,
                "ignored",
                "malformed or non-json payload"
            ));
            LOGGER.warn("EV12 webhook payload could not be parsed for alarm updates.");
        }
        return alarmAttempts;
    }

    private List<JsonNode> candidatePayloads(JsonNode root) {
        List<JsonNode> candidates = new ArrayList<>();
        if (root == null || root.isMissingNode() || root.isNull()) {
            return candidates;
        }

        appendCandidatePayload(root, candidates);
        return candidates;
    }

    private void appendCandidatePayload(JsonNode node, List<JsonNode> candidates) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                appendCandidatePayload(item, candidates);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        candidates.add(node);
        collectResponseCandidates(node, candidates);
    }

    private void collectResponseCandidates(JsonNode node, List<JsonNode> candidates) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        JsonNode response = node.path("response");
        if (!response.isMissingNode() && !response.isNull()) {
            addCandidateNode(response, candidates);
        }

        JsonNode nestedData = firstPresentNode(node, "data", "Data");
        if (!nestedData.isMissingNode() && !nestedData.isNull()) {
            JsonNode nestedResponse = nestedData.path("response");
            if (!nestedResponse.isMissingNode() && !nestedResponse.isNull()) {
                addCandidateNode(nestedResponse, candidates);
            }
        }
    }

    private void addCandidateNode(JsonNode candidateNode, List<JsonNode> candidates) {
        if (candidateNode.isArray()) {
            for (JsonNode item : candidateNode) {
                addCandidateNode(item, candidates);
            }
            return;
        }

        if (candidateNode.isObject()) {
            appendCandidatePayload(candidateNode, candidates);
            return;
        }

        if (candidateNode.isTextual()) {
            JsonNode parsed = parseJson(candidateNode.asText());
            if (parsed != null) {
                addCandidateNode(parsed, candidates);
            }
        }
    }

    private JsonNode parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value.trim());
        } catch (Exception ignored) {
            return null;
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
        if (node == null || node.isMissingNode() || node.isNull()) {
            return JsonNodeFactory.instance.missingNode();
        }

        for (String fieldName : fieldNames) {
            JsonNode candidate = node.path(fieldName);
            if (!candidate.isMissingNode()) {
                return candidate;
            }
        }
        return JsonNodeFactory.instance.missingNode();
    }

    private String deriveAlarmCode(JsonNode alarmCodeNode) {
        String latestMatch = null;
        for (String alarmCodeValue : alarmCodeValues(alarmCodeNode)) {
            if (isSosLike(alarmCodeValue) || isFallLike(alarmCodeValue)) {
                latestMatch = alarmCodeValue;
            }
        }
        return latestMatch;
    }

    private List<String> alarmCodeValues(JsonNode alarmCodeNode) {
        List<String> values = new ArrayList<>();
        if (alarmCodeNode == null || alarmCodeNode.isMissingNode() || alarmCodeNode.isNull()) {
            return values;
        }

        if (alarmCodeNode.isArray()) {
            for (JsonNode item : alarmCodeNode) {
                String value = normalizeAlarmCodeValue(item.asText(null));
                if (value != null) {
                    values.add(value);
                }
            }
            return values;
        }

        String singleValue = normalizeAlarmCodeValue(alarmCodeNode.asText(null));
        if (singleValue != null) {
            values.add(singleValue);
        }
        return values;
    }

    private String normalizeAlarmCodeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isSosLike(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains("sos");
    }

    private boolean isFallLike(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains("fall");
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

        Instant createdAt = parseTimestamp(root.path("createdAt"));
        if (createdAt != null) {
            return createdAt;
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
