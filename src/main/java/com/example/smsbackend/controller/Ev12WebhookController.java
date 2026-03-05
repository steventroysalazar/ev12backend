package com.example.smsbackend.controller;

import com.example.smsbackend.dto.Ev12WebhookEventResponse;
import com.example.smsbackend.service.Ev12WebhookService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class Ev12WebhookController {

    private final Ev12WebhookService ev12WebhookService;

    public Ev12WebhookController(Ev12WebhookService ev12WebhookService) {
        this.ev12WebhookService = ev12WebhookService;
    }

    @PostMapping("/ev12")
    public ResponseEntity<Map<String, Object>> ingest(
        @RequestBody String rawPayload,
        @RequestHeader(value = "X-Webhook-Token", required = false) String webhookToken
    ) {
        Ev12WebhookEventResponse saved = ev12WebhookService.ingest(rawPayload, webhookToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "event", saved
        ));
    }

    @GetMapping("/ev12/events")
    public ResponseEntity<List<Ev12WebhookEventResponse>> recent(
        @RequestParam(defaultValue = "20") Integer limit,
        @RequestHeader(value = "X-Webhook-Token", required = false) String webhookToken
    ) {
        return ResponseEntity.ok(ev12WebhookService.recentEvents(limit, webhookToken));
    }
}
