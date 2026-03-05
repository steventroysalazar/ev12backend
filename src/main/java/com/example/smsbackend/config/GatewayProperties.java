package com.example.smsbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
    String baseUrl,
    String token,
    Integer defaultLimit
) {
    public GatewayProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://192.168.1.55:8082";
        }
        if (defaultLimit == null || defaultLimit < 1) {
            defaultLimit = 100;
        }
    }
}
