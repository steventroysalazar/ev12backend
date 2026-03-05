package com.example.smsbackend.controller;

import com.example.smsbackend.config.GatewayProperties;
import com.example.smsbackend.dto.GatewayReplyMessage;
import com.example.smsbackend.dto.GatewayRequestOptions;
import com.example.smsbackend.dto.SendMessageRequest;
import com.example.smsbackend.dto.SendMessageResponse;
import com.example.smsbackend.service.GatewayClientService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final GatewayClientService gatewayClientService;
    private final GatewayProperties gatewayProperties;

    public MessageController(GatewayClientService gatewayClientService, GatewayProperties gatewayProperties) {
        this.gatewayClientService = gatewayClientService;
        this.gatewayProperties = gatewayProperties;
    }

    @PostMapping("/send")
    public ResponseEntity<SendMessageResponse> send(
        @Valid @RequestBody SendMessageRequest request,
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank() ? gatewayToken : legacyGatewayToken;
        gatewayClientService.sendMessage(request, new GatewayRequestOptions(gatewayBaseUrl, resolvedToken));
        return ResponseEntity.ok(new SendMessageResponse(true, "Message sent"));
    }

    @GetMapping("/replies")
    public ResponseEntity<List<GatewayReplyMessage>> replies(
        @RequestParam(required = false) String phone,
        @RequestParam(required = false) Long since,
        @RequestParam(required = false) Integer limit,
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank() ? gatewayToken : legacyGatewayToken;
        return ResponseEntity.ok(gatewayClientService.fetchMessages(
            phone,
            since,
            limit,
            new GatewayRequestOptions(gatewayBaseUrl, resolvedToken)
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank() ? gatewayToken : legacyGatewayToken;
        gatewayClientService.fetchMessages(null, null, 1, new GatewayRequestOptions(gatewayBaseUrl, resolvedToken));
        return ResponseEntity.ok(Map.of("success", true, "message", "Gateway reachable"));
    }

    @GetMapping("/debug/config")
    public ResponseEntity<Map<String, Object>> debugConfig(
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        String resolvedBaseUrl = gatewayBaseUrl != null && !gatewayBaseUrl.isBlank()
            ? gatewayBaseUrl.trim()
            : gatewayProperties.baseUrl();

        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank()
            ? gatewayToken
            : (legacyGatewayToken != null && !legacyGatewayToken.isBlank() ? legacyGatewayToken : gatewayProperties.token());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "resolvedBaseUrl", resolvedBaseUrl,
            "resolvedTokenPreview", maskToken(resolvedToken),
            "headerAuthorizationProvided", gatewayToken != null && !gatewayToken.isBlank(),
            "headerLegacyTokenProvided", legacyGatewayToken != null && !legacyGatewayToken.isBlank(),
            "headerBaseUrlProvided", gatewayBaseUrl != null && !gatewayBaseUrl.isBlank()
        ));
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "(empty)";
        }

        String trimmed = token.trim();
        int keep = Math.min(4, trimmed.length());
        return "****" + trimmed.substring(trimmed.length() - keep);
    }
}
