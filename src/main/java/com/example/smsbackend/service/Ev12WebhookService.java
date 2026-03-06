package com.example.smsbackend.service;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.example.smsbackend.entity.Ev12WebhookEvent;
import com.example.smsbackend.repository.Ev12WebhookEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Ev12WebhookService {

    private final Ev12WebhookEventRepository repository;
    private final WebhookProperties webhookProperties;
    private final ObjectMapper objectMapper;

    public Ev12WebhookService(
        Ev12WebhookEventRepository repository,
        WebhookProperties webhookProperties,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.webhookProperties = webhookProperties;
        this.objectMapper = objectMapper;
    }

    public Ev12WebhookEventResponse ingest(
        byte[] rawPayload,
        String contentType,
        String providedToken,
        Map<String, String> rawHeaders
    ) {
        validateToken(providedToken);

        Ev12WebhookEvent event = new Ev12WebhookEvent();
        event.setReceivedAt(Instant.now());
        event.setPayloadJson(serializePayload(rawPayload, contentType, rawHeaders));

        Ev12WebhookEvent saved = repository.save(event);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<Ev12WebhookEventResponse> recentEvents(int limit, String providedToken) {
        validateToken(providedToken);
        int normalizedLimit = Math.max(1, Math.min(limit, 3));
        return repository.findTop3ByOrderByReceivedAtDesc().stream()
            .limit(normalizedLimit)
            .map(this::toResponse)
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

    private Ev12WebhookEventResponse toResponse(Ev12WebhookEvent event) {
        return new Ev12WebhookEventResponse(
            event.getId(),
            event.getReceivedAt(),
            event.getPayloadJson()
        );
    }
}
