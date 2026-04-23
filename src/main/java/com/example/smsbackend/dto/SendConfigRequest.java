package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public record SendConfigRequest(
    @NotNull Long deviceId,
    String command,
    String imei,
    String eviewVersion,
    List<DeviceContactSettings> contacts,
    String contactNumber,
    Integer contactSlot,
    Boolean contactSmsEnabled,
    Boolean contactCallEnabled,
    String contactName,
    String smsPassword,
    Boolean smsWhitelistEnabled,
    Boolean requestLocation,
    Boolean requestGpsLocation,
    Boolean requestLbsLocation,
    @JsonAlias({"wifi_enabled", "wifiPositioning", "wifi_positioning"})
    Boolean wifiEnabled,
    Boolean bluetoothEnabled,
    Integer micVolume,
    Integer speakerVolume,
    Boolean vibrationEnabled,
    Boolean beepEnabled,
    Boolean prefixEnabled,
    String prefixName,
    Boolean checkBattery,
    Integer sosMode,
    Integer sosActionTime,
    String sosCallRingTime,
    String sosCallTalkTime,
    Boolean fallDownEnabled,
    Integer fallDownSensitivity,
    Boolean fallDownCall,
    Boolean noMotionEnabled,
    String noMotionTime,
    Boolean noMotionCall,
    Boolean motionEnabled,
    String motionStaticTime,
    String motionDurationTime,
    Boolean motionCall,
    Boolean overSpeedEnabled,
    String overSpeedLimit,
    Boolean geoFenceEnabled,
    Integer geoFenceMode,
    String geoFenceRadius,
    Boolean apnEnabled,
    String apn,
    Boolean serverEnabled,
    String serverHost,
    Integer serverPort,
    Boolean gprsEnabled,
    String workingMode,
    String workingModeInterval,
    String workingModeNoMotionInterval,
    String continuousLocateInterval,
    String continuousLocateDuration,
    String timeZone,
    Boolean turnOffDevice,
    Boolean findMyDevice,
    Boolean heartRateEnabled,
    String heartRateInterval,
    Boolean stepDetectionEnabled,
    String stepDetectionInterval,
    Boolean checkStatus,
    @JsonAlias({"authorized_numbers", "whitelistedNumbers", "whitelisted_numbers"})
    List<String> authorizedNumbers,
    @JsonAlias("geo_fences")
    List<GeoFenceSetting> geoFences
) {

    public DeviceProtocolSettings toDeviceProtocolSettings() {
        return new DeviceProtocolSettings(
            persistedContacts(),
            imei,
            eviewVersion,
            contactNumber,
            contactSlot,
            contactSmsEnabled,
            contactCallEnabled,
            contactName,
            smsPassword,
            smsWhitelistEnabled,
            requestLocation,
            requestGpsLocation,
            requestLbsLocation,
            wifiEnabled == null ? null : (wifiEnabled ? "1" : "0"),
            bluetoothEnabled,
            micVolume,
            speakerVolume,
            vibrationEnabled,
            beepEnabled,
            prefixEnabled,
            prefixName,
            checkBattery,
            sosMode,
            sosActionTime,
            sosCallRingTime,
            sosCallTalkTime,
            fallDownEnabled,
            fallDownSensitivity,
            fallDownCall,
            noMotionEnabled,
            noMotionTime,
            noMotionCall,
            motionEnabled,
            motionStaticTime,
            motionDurationTime,
            motionCall,
            overSpeedEnabled,
            overSpeedLimit,
            geoFenceEnabled,
            geoFenceMode,
            geoFenceRadius,
            apnEnabled,
            apn,
            serverEnabled,
            serverHost,
            serverPort,
            gprsEnabled,
            workingMode,
            workingModeInterval,
            workingModeNoMotionInterval,
            continuousLocateInterval,
            continuousLocateDuration,
            timeZone,
            turnOffDevice,
            findMyDevice,
            heartRateEnabled,
            heartRateInterval,
            stepDetectionEnabled,
            stepDetectionInterval,
            checkStatus,
            authorizedNumbers,
            geoFences
        );
    }

    private List<DeviceContactSettings> persistedContacts() {
        if (contacts != null && !contacts.isEmpty()) {
            return contacts;
        }

        if (!StringUtils.hasText(contactNumber)) {
            return null;
        }

        return List.of(new DeviceContactSettings(
            contactSlot,
            contactSmsEnabled,
            contactCallEnabled,
            contactNumber,
            contactName
        ));
    }

    public List<DeviceContactSettings> normalizedContacts() {
        if (contacts != null && !contacts.isEmpty()) {
            return contacts;
        }

        if (!StringUtils.hasText(contactNumber)) {
            return List.of();
        }

        List<DeviceContactSettings> result = new ArrayList<>();
        result.add(new DeviceContactSettings(
            contactSlot,
            contactSmsEnabled,
            contactCallEnabled,
            contactNumber,
            contactName
        ));
        return result;
    }

}
