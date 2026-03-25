package com.example.smsbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "webhook")
public record WebhookProperties(
    String ev12Token,
    boolean ev12PersistEvents,
    int ev12InMemoryEventsMax
) {
}
