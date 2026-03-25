package com.example.smsbackend.service;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.example.smsbackend.dto.WebhookAlarmAttemptResponse;
import com.example.smsbackend.entity.Ev12WebhookEvent;
import com.example.smsbackend.repository.Ev12WebhookEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final DeviceLocationUpdateService deviceLocationUpdateService;
    private final Ev12WebhookEventRepository ev12WebhookEventRepository;
    private final Deque<Ev12WebhookEventResponse> inMemoryEvents = new ArrayDeque<>();
    private final AtomicLong inMemoryEventSequence = new AtomicLong(0);

    public Ev12WebhookService(
        WebhookProperties webhookProperties,
        ObjectMapper objectMapper,
        AlarmCodeUpdateWorkerService alarmCodeUpdateWorkerService,
        DeviceLocationUpdateService deviceLocationUpdateService,
        Ev12WebhookEventRepository ev12WebhookEventRepository
    ) {
        this.webhookProperties = webhookProperties;
        this.objectMapper = objectMapper;
        this.alarmCodeUpdateWorkerService = alarmCodeUpdateWorkerService;
        this.deviceLocationUpdateService = deviceLocationUpdateService;
        this.ev12WebhookEventRepository = ev12WebhookEventRepository;
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

        Instant receivedAt = Instant.now();
        String payloadJson = serializePayload(rawPayload, contentType, rawHeaders);
        String alarmAttemptsJson = serializeAlarmAttempts(alarmAttempts);
        if (webhookProperties.ev12PersistEvents()) {
            Ev12WebhookEvent saved = persistWebhookEvent(payloadJson, alarmAttemptsJson, alarmAttempts, receivedAt);
            return toResponse(saved);
        }
        return persistInMemoryEvent(payloadJson, alarmAttempts, receivedAt);
    }

    public synchronized List<Ev12WebhookEventResponse> recentEvents(Integer limit, String providedToken) {
        validateToken(providedToken);
        int normalizedLimit = limit == null ? 200 : Math.max(1, Math.min(limit, 500));
        if (!webhookProperties.ev12PersistEvents()) {
            return inMemoryEvents.stream().limit(normalizedLimit).toList();
        }
        return ev12WebhookEventRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(
            0,
            normalizedLimit,
            Sort.by(Sort.Direction.DESC, "receivedAt")
        ))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public synchronized int clearEvents(String providedToken) {
        validateToken(providedToken);
        if (!webhookProperties.ev12PersistEvents()) {
            int deletedCount = inMemoryEvents.size();
            inMemoryEvents.clear();
            return deletedCount;
        }

        int deletedCount = Math.toIntExact(ev12WebhookEventRepository.count());
        ev12WebhookEventRepository.deleteAll();
        return deletedCount;
    }

    private Ev12WebhookEventResponse persistInMemoryEvent(
        String payloadJson,
        List<WebhookAlarmAttemptResponse> alarmAttempts,
        Instant receivedAt
    ) {
        int maxInMemoryEvents = Math.max(1, webhookProperties.ev12InMemoryEventsMax());
        Long eventId = inMemoryEventSequence.incrementAndGet();
        Ev12WebhookEventResponse event = new Ev12WebhookEventResponse(
            eventId,
            receivedAt,
            payloadJson,
            alarmAttempts == null ? List.of() : List.copyOf(alarmAttempts)
        );
        inMemoryEvents.addFirst(event);
        while (inMemoryEvents.size() > maxInMemoryEvents) {
            inMemoryEvents.removeLast();
        }
        return event;
    }

    private Ev12WebhookEvent persistWebhookEvent(
        String payloadJson,
        String alarmAttemptsJson,
        List<WebhookAlarmAttemptResponse> alarmAttempts,
        Instant receivedAt
    ) {
        Ev12WebhookEvent entity = new Ev12WebhookEvent();
        entity.setReceivedAt(receivedAt);
        entity.setPayloadJson(payloadJson);
        entity.setAlarmAttemptsJson(alarmAttemptsJson);
        WebhookAlarmAttemptResponse primary = alarmAttempts.stream().findFirst().orElse(null);
        if (primary != null) {
            entity.setDeviceId(primary.externalDeviceId());
            entity.setImei(primary.externalDeviceId());
            entity.setDeviceTimestamp(primary.eventTimestamp());
        }
        return ev12WebhookEventRepository.save(entity);
    }

    private String serializeAlarmAttempts(List<WebhookAlarmAttemptResponse> alarmAttempts) {
        try {
            return objectMapper.writeValueAsString(alarmAttempts == null ? List.of() : alarmAttempts);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize alarm attempts", exception);
        }
    }

    private Ev12WebhookEventResponse toResponse(Ev12WebhookEvent event) {
        return new Ev12WebhookEventResponse(
            event.getId(),
            event.getReceivedAt(),
            event.getPayloadJson(),
            deserializeAlarmAttempts(event.getAlarmAttemptsJson())
        );
    }

    private List<WebhookAlarmAttemptResponse> deserializeAlarmAttempts(String alarmAttemptsJson) {
        if (!StringUtils.hasText(alarmAttemptsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(alarmAttemptsJson, new TypeReference<List<WebhookAlarmAttemptResponse>>() {});
        } catch (Exception exception) {
            LOGGER.warn("Unable to deserialize stored alarm attempts JSON.", exception);
            return List.of();
        }
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

                Instant eventTimestamp = extractEventTimestamp(candidatePayload);
                Coordinates coordinates = extractCoordinates(candidatePayload);
                if (coordinates != null) {
                    deviceLocationUpdateService.applyNow(
                        externalDeviceId.trim(),
                        coordinates.latitude(),
                        coordinates.longitude(),
                        eventTimestamp
                    );
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

                AlarmCodeUpdateResult updateResult = alarmCodeUpdateWorkerService.applyNow(new AlarmCodeUpdateRequest(
                    externalDeviceId.trim(),
                    nextAlarmCode,
                    eventTimestamp
                ));
                WebhookAlarmAttemptResponse attempt = new WebhookAlarmAttemptResponse(
                    candidateIndex++,
                    externalDeviceId.trim(),
                    nextAlarmCode,
                    eventTimestamp,
                    updateResult.action(),
                    updateResult.reason()
                );
                alarmAttempts.add(attempt);
                LOGGER.info(
                    "EV12 webhook alarm update result: deviceId='{}', alarmCode='{}', eventTimestamp='{}', action='{}', reason='{}'",
                    attempt.externalDeviceId(),
                    attempt.alarmCode(),
                    attempt.eventTimestamp(),
                    attempt.action(),
                    attempt.reason()
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

    private JsonNode extractGpsLocationNode(JsonNode root) {
        JsonNode topLevel = firstPresentNode(root, "GPS Location", "gpsLocation", "gps_location");
        if (!topLevel.isMissingNode() && !topLevel.isNull()) {
            return topLevel;
        }

        JsonNode data = firstPresentNode(root, "data", "Data");
        if (!data.isMissingNode() && !data.isNull()) {
            JsonNode nested = firstPresentNode(data, "GPS Location", "gpsLocation", "gps_location");
            if (!nested.isMissingNode() && !nested.isNull()) {
                return nested;
            }
        }

        return JsonNodeFactory.instance.missingNode();
    }

    private Coordinates extractCoordinates(JsonNode root) {
        JsonNode gpsNode = extractGpsLocationNode(root);
        if (gpsNode.isMissingNode() || gpsNode.isNull()) {
            return null;
        }

        if (gpsNode.isArray()) {
            for (JsonNode item : gpsNode) {
                Coordinates coordinates = parseCoordinates(item);
                if (coordinates != null) {
                    return coordinates;
                }
            }
            return null;
        }
        return parseCoordinates(gpsNode);
    }

    private Coordinates parseCoordinates(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return null;
        }

        Double latitude = parseDouble(firstPresentNode(node, "latitude", "lat"));
        Double longitude = parseDouble(firstPresentNode(node, "longitude", "lng", "lon"));
        if (latitude == null || longitude == null) {
            return null;
        }
        return new Coordinates(latitude, longitude);
    }

    private Double parseDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (!node.isTextual()) {
            return null;
        }
        try {
            return Double.parseDouble(node.asText().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
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
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains("sos")) {
            return false;
        }
        return !normalized.contains("ending");
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

    private record Coordinates(Double latitude, Double longitude) {}
}
