package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public record SendConfigRequest(
    @NotNull
    @JsonAlias("device_id")
    Long deviceId,
    @JsonAlias("command")
    String command,
    @JsonAlias("imei")
    String imei,
    @JsonAlias("eview_version")
    String eviewVersion,
    @JsonAlias("contacts")
    List<DeviceContactSettings> contacts,
    @JsonAlias("contact_number")
    String contactNumber,
    @JsonAlias("contact_slot")
    Integer contactSlot,
    @JsonAlias("contact_sms_enabled")
    Boolean contactSmsEnabled,
    @JsonAlias("contact_call_enabled")
    Boolean contactCallEnabled,
    @JsonAlias("contact_name")
    String contactName,
    @JsonAlias("sms_password")
    String smsPassword,
    @JsonAlias("sms_whitelist_enabled")
    Boolean smsWhitelistEnabled,
    @JsonAlias("request_location")
    Boolean requestLocation,
    @JsonAlias("request_gps_location")
    Boolean requestGpsLocation,
    @JsonAlias("request_lbs_location")
    Boolean requestLbsLocation,
    @JsonAlias({"wifi_enabled", "wifiPositioning", "wifi_positioning"})
    Boolean wifiEnabled,
    @JsonAlias("bluetooth_enabled")
    Boolean bluetoothEnabled,
    @JsonAlias("mic_volume")
    Integer micVolume,
    @JsonAlias("speaker_volume")
    Integer speakerVolume,
    @JsonAlias("vibration_enabled")
    Boolean vibrationEnabled,
    @JsonAlias("beep_enabled")
    Boolean beepEnabled,
    @JsonAlias("prefix_enabled")
    Boolean prefixEnabled,
    @JsonAlias("prefix_name")
    String prefixName,
    @JsonAlias("check_battery")
    Boolean checkBattery,
    @JsonAlias("sos_mode")
    Integer sosMode,
    @JsonAlias("sos_action_time")
    Integer sosActionTime,
    @JsonAlias("sos_call_ring_time")
    String sosCallRingTime,
    @JsonAlias("sos_call_talk_time")
    String sosCallTalkTime,
    @JsonAlias("fall_down_enabled")
    Boolean fallDownEnabled,
    @JsonAlias("fall_down_sensitivity")
    Integer fallDownSensitivity,
    @JsonAlias("fall_down_call")
    Boolean fallDownCall,
    @JsonAlias("no_motion_enabled")
    Boolean noMotionEnabled,
    @JsonAlias("no_motion_time")
    String noMotionTime,
    @JsonAlias("no_motion_call")
    Boolean noMotionCall,
    @JsonAlias("motion_enabled")
    Boolean motionEnabled,
    @JsonAlias("motion_static_time")
    String motionStaticTime,
    @JsonAlias("motion_duration_time")
    String motionDurationTime,
    @JsonAlias("motion_call")
    Boolean motionCall,
    @JsonAlias("over_speed_enabled")
    Boolean overSpeedEnabled,
    @JsonAlias("over_speed_limit")
    String overSpeedLimit,
    @JsonAlias("geo_fence_enabled")
    Boolean geoFenceEnabled,
    @JsonAlias("geo_fence_mode")
    Integer geoFenceMode,
    @JsonAlias("geo_fence_radius")
    String geoFenceRadius,
    @JsonAlias("apn_enabled")
    Boolean apnEnabled,
    @JsonAlias("apn")
    String apn,
    @JsonAlias("server_enabled")
    Boolean serverEnabled,
    @JsonAlias("server_host")
    String serverHost,
    @JsonAlias("server_port")
    Integer serverPort,
    @JsonAlias("gprs_enabled")
    Boolean gprsEnabled,
    @JsonAlias("working_mode")
    String workingMode,
    @JsonAlias("working_mode_interval")
    String workingModeInterval,
    @JsonAlias("working_mode_no_motion_interval")
    String workingModeNoMotionInterval,
    @JsonAlias("continuous_locate_interval")
    String continuousLocateInterval,
    @JsonAlias("continuous_locate_duration")
    String continuousLocateDuration,
    @JsonAlias("time_zone")
    String timeZone,
    @JsonAlias("turn_off_device")
    Boolean turnOffDevice,
    @JsonAlias("find_my_device")
    Boolean findMyDevice,
    @JsonAlias("heart_rate_enabled")
    Boolean heartRateEnabled,
    @JsonAlias("heart_rate_interval")
    String heartRateInterval,
    @JsonAlias("step_detection_enabled")
    Boolean stepDetectionEnabled,
    @JsonAlias("step_detection_interval")
    String stepDetectionInterval,
    @JsonAlias("check_status")
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
