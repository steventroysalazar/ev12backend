package com.example.smsbackend.service;

import com.example.smsbackend.dto.DeviceContactSettings;
import com.example.smsbackend.dto.SendConfigRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceCommandService {

    private static final String COMMAND_SEPARATOR = ",";

    public List<String> buildCommands(SendConfigRequest request) {
        List<String> commands = new ArrayList<>();
        appendContactCommands(request.normalizedContacts(), commands);
        if (StringUtils.hasText(request.smsPassword())) {
            commands.add("P" + request.smsPassword().trim());
        }
        if (request.smsWhitelistEnabled() != null) {
            commands.add("sms" + (request.smsWhitelistEnabled() ? 1 : 0));
        }
        if (Boolean.TRUE.equals(request.requestLocation())) {
            commands.add("loc");
        }
        if (Boolean.TRUE.equals(request.requestGpsLocation())) {
            commands.add("loc,gps");
        }
        if (Boolean.TRUE.equals(request.requestLbsLocation())) {
            commands.add("LBS1");
        }
        if (request.sosMode() != null && request.sosActionTime() != null) {
            commands.add("SOS" + request.sosMode() + "," + request.sosActionTime());
        }
        if (StringUtils.hasText(request.sosCallRingTime()) && StringUtils.hasText(request.sosCallTalkTime())) {
            commands.add("soscall" + request.sosCallRingTime().trim() + "," + request.sosCallTalkTime().trim());
        }
        if (request.wifiEnabled() != null) {
            commands.add("Wifi" + (request.wifiEnabled() ? 1 : 0));
        }
        if (request.bluetoothEnabled() != null) {
            commands.add("BLE" + (request.bluetoothEnabled() ? 1 : 0));
        }
        if (request.micVolume() != null) {
            commands.add("Micvolume" + request.micVolume());
        }
        if (request.speakerVolume() != null) {
            commands.add("Speakervolume" + request.speakerVolume());
        }
        if (request.vibrationEnabled() != null) {
            commands.add("Vibrate" + (request.vibrationEnabled() ? 1 : 0));
        }
        if (request.beepEnabled() != null) {
            commands.add("Beep" + (request.beepEnabled() ? 1 : 0));
        }
        if (request.prefixEnabled() != null && StringUtils.hasText(request.prefixName())) {
            commands.add("prefix" + (request.prefixEnabled() ? 1 : 0) + "," + request.prefixName().trim());
        }
        if (Boolean.TRUE.equals(request.checkBattery())) {
            commands.add("battery");
        }
        if (request.fallDownEnabled() != null && request.fallDownSensitivity() != null && request.fallDownCall() != null) {
            commands.add("fl" + (request.fallDownEnabled() ? 1 : 0) + "," + request.fallDownSensitivity()
                + "," + (request.fallDownCall() ? 1 : 0));
        }
        if (request.noMotionEnabled() != null && StringUtils.hasText(request.noMotionTime()) && request.noMotionCall() != null) {
            commands.add("nmo" + (request.noMotionEnabled() ? 1 : 0) + "," + request.noMotionTime().trim()
                + "," + (request.noMotionCall() ? 1 : 0));
        }
        if (request.motionEnabled() != null
            && StringUtils.hasText(request.motionStaticTime())
            && StringUtils.hasText(request.motionDurationTime())
            && request.motionCall() != null) {
            commands.add("mo" + (request.motionEnabled() ? 1 : 0)
                + "," + request.motionStaticTime().trim()
                + "," + request.motionDurationTime().trim()
                + "," + (request.motionCall() ? 1 : 0));
        }
        if (request.overSpeedEnabled() != null && StringUtils.hasText(request.overSpeedLimit())) {
            commands.add("Speed" + (request.overSpeedEnabled() ? 1 : 0) + "," + request.overSpeedLimit().trim());
        }
        if (request.geoFenceEnabled() != null && request.geoFenceMode() != null && StringUtils.hasText(request.geoFenceRadius())) {
            commands.add("Geo1," + (request.geoFenceEnabled() ? 1 : 0) + "," + request.geoFenceMode() + "," + request.geoFenceRadius().trim());
        }
        if (request.apnEnabled() != null && StringUtils.hasText(request.apn())) {
            commands.add("S" + (request.apnEnabled() ? 1 : 0) + "," + request.apn().trim());
        }
        if (request.serverEnabled() != null && StringUtils.hasText(request.serverHost()) && request.serverPort() != null) {
            commands.add("IP" + (request.serverEnabled() ? 1 : 0) + "," + request.serverHost().trim() + "," + request.serverPort());
        }
        if (request.gprsEnabled() != null) {
            commands.add("S" + (request.gprsEnabled() ? 2 : 0));
        }

        appendWorkingModeCommands(request, commands);

        if (StringUtils.hasText(request.continuousLocateInterval()) && StringUtils.hasText(request.continuousLocateDuration())) {
            commands.add("CL" + request.continuousLocateInterval().trim() + "," + request.continuousLocateDuration().trim());
        }
        if (StringUtils.hasText(request.timeZone())) {
            commands.add("tz" + request.timeZone().trim());
        }
        if (Boolean.TRUE.equals(request.turnOffDevice())) {
            commands.add("off");
        }
        if (Boolean.TRUE.equals(request.findMyDevice())) {
            commands.add("findme");
        }
        if (request.heartRateEnabled() != null && StringUtils.hasText(request.heartRateInterval())) {
            commands.add("hrs" + (request.heartRateEnabled() ? 1 : 0) + "," + request.heartRateInterval().trim());
        }
        if (request.stepDetectionEnabled() != null && StringUtils.hasText(request.stepDetectionInterval())) {
            commands.add("detpedo" + (request.stepDetectionEnabled() ? 1 : 0) + "," + request.stepDetectionInterval().trim());
        }
        if (Boolean.TRUE.equals(request.checkStatus())) {
            commands.add("status");
        }

        return commands;
    }

    private void appendContactCommands(List<DeviceContactSettings> contacts, List<String> commands) {
        if (contacts == null || contacts.isEmpty()) {
            return;
        }

        int limit = Math.min(10, contacts.size());
        for (int index = 0; index < limit; index++) {
            DeviceContactSettings contact = contacts.get(index);
            if (contact == null || !StringUtils.hasText(contact.phone())) {
                continue;
            }

            int fallbackSlot = index + 1;
            int slot = contact.slot() != null && contact.slot() >= 1 && contact.slot() <= 10 ? contact.slot() : fallbackSlot;
            int sms = contact.smsEnabled() == null || contact.smsEnabled() ? 1 : 0;
            int call = contact.callEnabled() == null || contact.callEnabled() ? 1 : 0;

            String base = "A" + slot + "," + sms + "," + call + "," + contact.phone().trim();
            if (StringUtils.hasText(contact.name())) {
                base += "," + contact.name().trim();
            }
            commands.add(base);
        }
    }

    public String buildPreview(List<String> commands) {
        return String.join(COMMAND_SEPARATOR, commands);
    }

    public List<String> splitForSms(String preview) {
        if (!StringUtils.hasText(preview)) {
            return List.of();
        }

        int chunkSize = 150;
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < preview.length(); start += chunkSize) {
            chunks.add(preview.substring(start, Math.min(preview.length(), start + chunkSize)));
        }
        return chunks;
    }

    private void appendWorkingModeCommands(SendConfigRequest request, List<String> commands) {
        if (!StringUtils.hasText(request.workingMode())) {
            return;
        }

        String mode = request.workingMode().trim();
        if ("mode1".equals(mode)) {
            commands.add("mode1");
            return;
        }

        if (("mode2".equals(mode) || "mode6".equals(mode))
            && StringUtils.hasText(request.workingModeInterval())
            && StringUtils.hasText(request.workingModeNoMotionInterval())) {
            commands.add(mode + "," + request.workingModeInterval().trim() + "," + request.workingModeNoMotionInterval().trim());
            return;
        }

        if (("mode3".equals(mode) || "mode4".equals(mode) || "mode5".equals(mode))
            && StringUtils.hasText(request.workingModeInterval())) {
            commands.add(mode + "," + request.workingModeInterval().trim());
        }
    }
}
