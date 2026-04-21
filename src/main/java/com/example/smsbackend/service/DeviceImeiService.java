package com.example.smsbackend.service;

import com.example.smsbackend.dto.GatewayReplyMessage;
import com.example.smsbackend.dto.GatewayRequestOptions;
import com.example.smsbackend.dto.SendMessageRequest;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.DeviceRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceImeiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceImeiService.class);
    private static final String IMEI_REQUEST_SMS = "V?";
    private static final Pattern IMEI_PATTERN = Pattern.compile("(?i)\\bIMEI\\s*[:=]\\s*([A-Za-z0-9-]{8,32})");

    private final GatewayClientService gatewayClientService;
    private final DeviceRepository deviceRepository;
    private final AtomicLong sinceEpochMillis = new AtomicLong(0);

    public DeviceImeiService(GatewayClientService gatewayClientService, DeviceRepository deviceRepository) {
        this.gatewayClientService = gatewayClientService;
        this.deviceRepository = deviceRepository;
    }

    public void requestImei(Device device, GatewayRequestOptions options) {
        if (device == null || !StringUtils.hasText(device.getPhoneNumber())) {
            LOGGER.warn("IMEI request skipped because device or phone number is missing.");
            return;
        }
        gatewayClientService.sendMessage(new SendMessageRequest(device.getPhoneNumber().trim(), IMEI_REQUEST_SMS, null), options);
    }

    public String imeiRequestCommand() {
        return IMEI_REQUEST_SMS;
    }

    @Scheduled(
        fixedDelayString = "${gateway.imei-poll-interval-ms:30000}",
        initialDelayString = "${gateway.imei-poll-initial-delay-ms:10000}"
    )
    public void pollForImeiReplies() {
        try {
            long since = sinceEpochMillis.get();
            Long querySince = since > 0 ? since : null;
            List<GatewayReplyMessage> replies = gatewayClientService.fetchMessages(null, querySince, 100, null);
            processReplies(replies);
        } catch (Exception exception) {
            LOGGER.warn("IMEI background polling failed: {}", exception.getMessage());
        }
    }

    public void processReplies(List<GatewayReplyMessage> replies) {
        if (replies == null || replies.isEmpty()) {
            return;
        }

        long maxDate = replies.stream().mapToLong(GatewayReplyMessage::date).max().orElse(0L);
        if (maxDate > 0) {
            sinceEpochMillis.updateAndGet(current -> Math.max(current, maxDate + 1));
        }

        replies.stream()
            .sorted(Comparator.comparingLong(GatewayReplyMessage::date))
            .forEach(this::processReply);
    }

    private void processReply(GatewayReplyMessage reply) {
        if (reply == null || !StringUtils.hasText(reply.from()) || !StringUtils.hasText(reply.message())) {
            return;
        }

        String imei = parseImei(reply.message());
        if (!StringUtils.hasText(imei)) {
            return;
        }

        resolveDeviceByPhone(reply.from().trim()).ifPresent(device -> {
            String normalizedImei = imei.trim();
            if (normalizedImei.equals(device.getExternalDeviceId())) {
                return;
            }

            device.setExternalDeviceId(normalizedImei);
            deviceRepository.save(device);
            LOGGER.info(
                "Updated device externalDeviceId from SMS reply. deviceId='{}', phone='{}', imei='{}', smsMessageId='{}', receivedAt='{}'",
                device.getId(),
                reply.from(),
                normalizedImei,
                reply.id(),
                Instant.ofEpochMilli(reply.date())
            );
        });
    }

    private Optional<Device> resolveDeviceByPhone(String phone) {
        Optional<Device> exact = deviceRepository.findByPhoneNumber(phone);
        if (exact.isPresent()) {
            return exact;
        }

        String normalizedPhone = normalizePhone(phone);
        if (!StringUtils.hasText(normalizedPhone)) {
            return Optional.empty();
        }

        return deviceRepository.findAll().stream()
            .filter(device -> normalizedPhone.equals(normalizePhone(device.getPhoneNumber())))
            .findFirst();
    }

    private String parseImei(String message) {
        Matcher matcher = IMEI_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return phone.replaceAll("[^0-9+]", "");
    }
}
