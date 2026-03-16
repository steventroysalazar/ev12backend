package com.example.smsbackend.service;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Ev12WebhookService {

    private static final int MAX_EVENTS = 3;

    private final WebhookProperties webhookProperties;
    private final ObjectMapper objectMapper;
    private final AtomicLong eventIdSequence = new AtomicLong(1);
    private final Deque<Ev12WebhookEventResponse> recentEvents = new ArrayDeque<>();

    public Ev12WebhookService(
        WebhookProperties webhookProperties,
        ObjectMapper objectMapper
    ) {
        this.webhookProperties = webhookProperties;
        this.objectMapper = objectMapper;
    }

    public synchronized Ev12WebhookEventResponse ingest(
        byte[] rawPayload,
        String contentType,
        String providedToken,
        Map<String, String> rawHeaders
    ) {
        validateToken(providedToken);

        Ev12WebhookEventResponse event = new Ev12WebhookEventResponse(
            eventIdSequence.getAndIncrement(),
            Instant.now(),
            serializePayload(rawPayload, contentType, rawHeaders)
        );

        recentEvents.addFirst(event);
        while (recentEvents.size() > MAX_EVENTS) {
            recentEvents.removeLast();
        }

        return event;
    }

    public synchronized List<Ev12WebhookEventResponse> recentEvents(int limit, String providedToken) {
        validateToken(providedToken);
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_EVENTS));
        return new ArrayList<>(recentEvents).stream()
            .limit(normalizedLimit)
            .toList();
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
