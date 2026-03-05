package com.example.smsbackend.controller;

import com.example.smsbackend.dto.GatewayReplyMessage;
import com.example.smsbackend.dto.GatewayRequestOptions;
import com.example.smsbackend.dto.InboundMessageResponse;
import com.example.smsbackend.dto.SendConfigRequest;
import com.example.smsbackend.dto.SendConfigResponse;
import com.example.smsbackend.dto.SendMessageRequest;
import com.example.smsbackend.dto.SentMessageResponse;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.service.DeviceCommandService;
import com.example.smsbackend.service.GatewayClientService;
import com.example.smsbackend.service.UserDeviceService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DeviceConfigController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final UserDeviceService userDeviceService;
    private final DeviceCommandService deviceCommandService;
    private final GatewayClientService gatewayClientService;

    public DeviceConfigController(
        UserDeviceService userDeviceService,
        DeviceCommandService deviceCommandService,
        GatewayClientService gatewayClientService
    ) {
        this.userDeviceService = userDeviceService;
        this.deviceCommandService = deviceCommandService;
        this.gatewayClientService = gatewayClientService;
    }

    @PostMapping("/send-config")
    public ResponseEntity<SendConfigResponse> sendConfig(
        @Valid @RequestBody SendConfigRequest request,
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        Device device = userDeviceService.getDevice(request.deviceId());
        List<String> commands = deviceCommandService.buildCommands(request);
        String commandPreview = deviceCommandService.buildPreview(commands);
        List<String> smsBodies = deviceCommandService.splitForSms(commandPreview);

        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank() ? gatewayToken : legacyGatewayToken;
        GatewayRequestOptions options = new GatewayRequestOptions(gatewayBaseUrl, resolvedToken);

        for (String body : smsBodies) {
            gatewayClientService.sendMessage(new SendMessageRequest(device.getPhoneNumber(), body, null), options);
        }

        List<SentMessageResponse> messages = smsBodies.stream().map(SentMessageResponse::new).toList();
        return ResponseEntity.ok(new SendConfigResponse(true, device.getId(), device.getPhoneNumber(), commandPreview, messages));
    }

    @GetMapping("/inbound-messages")
    public ResponseEntity<List<InboundMessageResponse>> inboundMessages(
        @RequestParam(required = false) Long since,
        @RequestParam(required = false) String phone,
        @RequestParam(required = false) Integer limit,
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        Long normalizedSince = normalizeSince(since);
        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank() ? gatewayToken : legacyGatewayToken;
        GatewayRequestOptions options = new GatewayRequestOptions(gatewayBaseUrl, resolvedToken);

        List<GatewayReplyMessage> replies = gatewayClientService.fetchMessages(phone, normalizedSince, limit, options);

        List<InboundMessageResponse> response = replies.stream().map(item -> new InboundMessageResponse(
            item.id(),
            item.from(),
            item.message(),
            FORMATTER.format(Instant.ofEpochMilli(item.date()).atOffset(ZoneOffset.UTC))
        )).toList();

        return ResponseEntity.ok(response);
    }

    private Long normalizeSince(Long since) {
        if (since == null) {
            return null;
        }

        if (since < 1_000_000_000_000L) {
            return since * 1000;
        }

        return since;
    }
}
