package com.example.smsbackend.controller;

import com.example.smsbackend.dto.GatewayReplyMessage;
import com.example.smsbackend.dto.GatewayRequestOptions;
import com.example.smsbackend.dto.InboundMessageResponse;
import com.example.smsbackend.dto.DeviceConfigStatusResponse;
import com.example.smsbackend.dto.ResendConfigResponse;
import com.example.smsbackend.dto.ResendImeiResponse;
import com.example.smsbackend.dto.SendConfigRequest;
import com.example.smsbackend.dto.SendConfigResponse;
import com.example.smsbackend.dto.SendMessageRequest;
import com.example.smsbackend.dto.SentMessageResponse;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.service.DeviceCommandService;
import com.example.smsbackend.service.DeviceImeiService;
import com.example.smsbackend.service.DeviceTelemetryLogService;
import com.example.smsbackend.service.GatewayClientService;
import com.example.smsbackend.service.UserDeviceService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class DeviceConfigController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final UserDeviceService userDeviceService;
    private final DeviceCommandService deviceCommandService;
    private final GatewayClientService gatewayClientService;
    private final DeviceRepository deviceRepository;
    private final DeviceTelemetryLogService deviceTelemetryLogService;
    private final DeviceImeiService deviceImeiService;

    public DeviceConfigController(
        UserDeviceService userDeviceService,
        DeviceCommandService deviceCommandService,
        GatewayClientService gatewayClientService,
        DeviceRepository deviceRepository,
        DeviceTelemetryLogService deviceTelemetryLogService,
        DeviceImeiService deviceImeiService
    ) {
        this.userDeviceService = userDeviceService;
        this.deviceCommandService = deviceCommandService;
        this.gatewayClientService = gatewayClientService;
        this.deviceRepository = deviceRepository;
        this.deviceTelemetryLogService = deviceTelemetryLogService;
        this.deviceImeiService = deviceImeiService;
    }

    @PostMapping("/send-config")
    public ResponseEntity<SendConfigResponse> sendConfig(
        @Valid @RequestBody SendConfigRequest request,
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        Device device = userDeviceService.getDevice(request.deviceId());
        userDeviceService.saveDeviceProtocolSettings(device, request.toDeviceProtocolSettings());
        String commandPreview = StringUtils.hasText(request.command())
            ? request.command().trim()
            : deviceCommandService.buildPreview(deviceCommandService.buildCommands(request));
        List<String> smsBodies = deviceCommandService.splitForSms(commandPreview);

        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank() ? gatewayToken : legacyGatewayToken;
        GatewayRequestOptions options = new GatewayRequestOptions(gatewayBaseUrl, resolvedToken);

        for (String body : smsBodies) {
            gatewayClientService.sendMessage(new SendMessageRequest(device.getPhoneNumber(), body, null), options);
        }
        userDeviceService.markDeviceConfigPending(device, commandPreview, Instant.now());

        List<SentMessageResponse> messages = smsBodies.stream().map(SentMessageResponse::new).toList();
        return ResponseEntity.ok(new SendConfigResponse(true, device.getId(), device.getPhoneNumber(), commandPreview, messages));
    }

    @GetMapping("/devices/{deviceId}/config-status")
    public ResponseEntity<DeviceConfigStatusResponse> configStatus(
        @PathVariable Long deviceId,
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        Device device = userDeviceService.getDevice(deviceId);
        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank() ? gatewayToken : legacyGatewayToken;
        GatewayRequestOptions options = new GatewayRequestOptions(gatewayBaseUrl, resolvedToken);
        syncPendingStatus(device, options);

        Instant nextResendAt = userDeviceService.nextResendAt(device);
        return ResponseEntity.ok(new DeviceConfigStatusResponse(
            device.getId(),
            device.getConfigStatus(),
            UserDeviceService.CONFIG_STATUS_PENDING.equals(device.getConfigStatus()),
            device.getConfigLastSentAt(),
            device.getConfigAppliedAt(),
            nextResendAt,
            device.getConfigCommandPreview()
        ));
    }

    @PostMapping("/devices/{deviceId}/config-resend")
    public ResponseEntity<ResendConfigResponse> resendConfig(
        @PathVariable Long deviceId,
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        Device device = userDeviceService.getDevice(deviceId);
        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank() ? gatewayToken : legacyGatewayToken;
        GatewayRequestOptions options = new GatewayRequestOptions(gatewayBaseUrl, resolvedToken);

        syncPendingStatus(device, options);

        if (!UserDeviceService.CONFIG_STATUS_PENDING.equals(device.getConfigStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending device configuration to resend.");
        }
        if (!StringUtils.hasText(device.getConfigCommandPreview())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing command preview for pending configuration.");
        }

        Instant now = Instant.now();
        if (!userDeviceService.canResend(device, now)) {
            Instant nextAllowed = userDeviceService.nextResendAt(device);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Config resend is allowed every 5 minutes. Try again at " + FORMATTER.format(nextAllowed.atOffset(ZoneOffset.UTC)) + ".");
        }

        List<String> smsBodies = deviceCommandService.splitForSms(device.getConfigCommandPreview());
        for (String body : smsBodies) {
            gatewayClientService.sendMessage(new SendMessageRequest(device.getPhoneNumber(), body, null), options);
        }
        userDeviceService.markDeviceConfigPending(device, device.getConfigCommandPreview(), now);

        return ResponseEntity.ok(new ResendConfigResponse(
            true,
            device.getId(),
            device.getConfigStatus(),
            device.getConfigLastSentAt(),
            smsBodies.stream().map(SentMessageResponse::new).toList()
        ));
    }


    @PostMapping("/devices/{deviceId}/imei-resend")
    public ResponseEntity<ResendImeiResponse> resendImeiLookup(
        @PathVariable Long deviceId,
        @RequestHeader(value = "X-Gateway-Base-Url", required = false) String gatewayBaseUrl,
        @RequestHeader(value = "Authorization", required = false) String gatewayToken,
        @RequestHeader(value = "X-Gateway-Token", required = false) String legacyGatewayToken
    ) {
        Device device = userDeviceService.getDevice(deviceId);
        String resolvedToken = gatewayToken != null && !gatewayToken.isBlank() ? gatewayToken : legacyGatewayToken;
        GatewayRequestOptions options = new GatewayRequestOptions(gatewayBaseUrl, resolvedToken);

        deviceImeiService.requestImei(device, options);

        return ResponseEntity.ok(new ResendImeiResponse(
            true,
            device.getId(),
            device.getPhoneNumber(),
            deviceImeiService.imeiRequestCommand(),
            Instant.now()
        ));
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
        deviceImeiService.processReplies(replies);
        replies.forEach(this::logSmsLocation);

        List<InboundMessageResponse> response = replies.stream().map(item -> new InboundMessageResponse(
            item.id(),
            item.from(),
            item.message(),
            FORMATTER.format(Instant.ofEpochMilli(item.date()).atOffset(ZoneOffset.UTC))
        )).toList();

        return ResponseEntity.ok(response);
    }

    private void logSmsLocation(GatewayReplyMessage message) {
        if (message == null || !StringUtils.hasText(message.from()) || !StringUtils.hasText(message.message())) {
            return;
        }

        deviceRepository.findByPhoneNumber(message.from().trim()).ifPresent(device ->
            deviceTelemetryLogService.logLocationFromSms(
                device,
                message.id(),
                message.message(),
                Instant.ofEpochMilli(message.date())
            )
        );
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

    private void syncPendingStatus(Device device, GatewayRequestOptions options) {
        if (!UserDeviceService.CONFIG_STATUS_PENDING.equals(device.getConfigStatus()) || device.getConfigLastSentAt() == null) {
            return;
        }

        List<GatewayReplyMessage> replies = gatewayClientService.fetchMessages(
            device.getPhoneNumber(),
            device.getConfigLastSentAt().toEpochMilli(),
            20,
            options
        );

        replies.stream()
            .filter(message -> device.getConfigLastSentAt() == null || message.date() >= device.getConfigLastSentAt().toEpochMilli())
            .findFirst()
            .ifPresent(message -> userDeviceService.markDeviceConfigApplied(device, Instant.ofEpochMilli(message.date())));
    }
}
