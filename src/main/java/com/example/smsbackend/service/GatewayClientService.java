package com.example.smsbackend.service;

import com.example.smsbackend.config.GatewayProperties;
import com.example.smsbackend.dto.GatewayReplyMessage;
import com.example.smsbackend.dto.GatewayRequestOptions;
import com.example.smsbackend.dto.SendMessageRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GatewayClientService {

    private static final int CONNECT_TIMEOUT_MS = (int) Duration.ofSeconds(5).toMillis();
    private static final int READ_TIMEOUT_MS = (int) Duration.ofSeconds(10).toMillis();

    private final GatewayProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GatewayClientService(GatewayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public void sendMessage(SendMessageRequest request, GatewayRequestOptions options) {
        String baseUrl = resolveBaseUrl(options);

        HttpHeaders headers = authHeaders(options);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", request.to());
        payload.put("message", request.message());
        if (request.slot() != null) {
            payload.put("slot", request.slot());
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String.class);
        } catch (HttpStatusCodeException e) {
            throw new GatewayClientException(
                e.getStatusCode().value(),
                "Gateway send failed: HTTP " + e.getStatusCode().value() + bodySuffix(e.getResponseBodyAsString()),
                e
            );
        } catch (ResourceAccessException e) {
            throw new IllegalStateException(
                "Cannot reach Android gateway at " + baseUrl +
                    ". Verify phone IP/port and that service is enabled.",
                e
            );
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new GatewayClientException(
                response.getStatusCode().value(),
                "Gateway send failed: HTTP " + response.getStatusCode().value() +
                    bodySuffix(response.getBody())
            );
        }
    }

    public List<GatewayReplyMessage> fetchMessages(String phone, Long since, Integer limit, GatewayRequestOptions options) {
        String baseUrl = resolveBaseUrl(options);

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/messages")
            .queryParamIfPresent("phone", optional(phone))
            .queryParamIfPresent("since", optional(since))
            .queryParam("limit", limit != null ? limit : properties.defaultLimit())
            .build(true)
            .toUriString();

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(options));

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        } catch (HttpStatusCodeException e) {
            throw new GatewayClientException(
                e.getStatusCode().value(),
                "Gateway fetch failed: HTTP " + e.getStatusCode().value() + bodySuffix(e.getResponseBodyAsString()),
                e
            );
        } catch (ResourceAccessException e) {
            throw new IllegalStateException(
                "Cannot reach Android gateway at " + baseUrl +
                    ". Verify phone IP/port and that service is enabled.",
                e
            );
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new GatewayClientException(
                response.getStatusCode().value(),
                "Gateway fetch failed: HTTP " + response.getStatusCode().value() +
                    bodySuffix(response.getBody())
            );
        }

        if (response.getBody() == null || response.getBody().isBlank()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(response.getBody(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse gateway response", e);
        }
    }

    private HttpHeaders authHeaders(GatewayRequestOptions options) {
        HttpHeaders headers = new HttpHeaders();

        String token = options != null && StringUtils.hasText(options.token())
            ? options.token()
            : properties.token();

        if (StringUtils.hasText(token)) {
            headers.set(HttpHeaders.AUTHORIZATION, token);
        }
        return headers;
    }

    private String resolveBaseUrl(GatewayRequestOptions options) {
        if (options != null && StringUtils.hasText(options.baseUrl())) {
            return options.baseUrl().trim();
        }
        return properties.baseUrl();
    }

    private String bodySuffix(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }

        String trimmed = body.trim();
        if (trimmed.length() > 300) {
            trimmed = trimmed.substring(0, 300) + "...";
        }
        return ", body: " + trimmed;
    }

    private static <T> java.util.Optional<T> optional(T value) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (value instanceof String s && s.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(value);
    }
}
