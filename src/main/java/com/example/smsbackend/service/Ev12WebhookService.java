package com.example.smsbackend.service;

import com.example.smsbackend.config.WebhookProperties;
import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.example.smsbackend.entity.Ev12WebhookEvent;
import com.example.smsbackend.repository.Ev12WebhookEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Ev12WebhookService {

    private final Ev12WebhookEventRepository repository;
    private final WebhookProperties webhookProperties;

    public Ev12WebhookService(
        Ev12WebhookEventRepository repository,
        WebhookProperties webhookProperties
    ) {
        this.repository = repository;
        this.webhookProperties = webhookProperties;
    }

    public Ev12WebhookEventResponse ingest(String rawPayload, String providedToken) {
        validateToken(providedToken);
        if (!StringUtils.hasText(rawPayload)) {
            throw new IllegalArgumentException("Webhook payload cannot be empty");
        }

        Ev12WebhookEvent event = new Ev12WebhookEvent();
        event.setReceivedAt(Instant.now());
        event.setPayloadJson(rawPayload.trim());

        Ev12WebhookEvent saved = repository.save(event);
        return toResponse(saved);
    }

    public List<Ev12WebhookEventResponse> recentEvents(int limit, String providedToken) {
        validateToken(providedToken);
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return repository.findTop100ByOrderByReceivedAtDesc().stream()
            .limit(normalizedLimit)
            .map(this::toResponse)
            .toList();
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
