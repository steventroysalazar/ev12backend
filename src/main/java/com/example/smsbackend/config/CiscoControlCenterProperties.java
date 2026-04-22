package com.example.smsbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cisco.control-center")
public record CiscoControlCenterProperties(
    boolean enabled,
    String host,
    String username,
    String apiKey,
    String deviceDetailsPath,
    String activatePath,
    String deactivatePath,
    int timeoutMs
) {
    public CiscoControlCenterProperties {
        if (deviceDetailsPath == null || deviceDetailsPath.isBlank()) {
            deviceDetailsPath = "/rws/api/v1/devices/{iccid}";
        }
        if (activatePath == null || activatePath.isBlank()) {
            activatePath = "/rws/api/v1/devices/{iccid}/activate";
        }
        if (deactivatePath == null || deactivatePath.isBlank()) {
            deactivatePath = "/rws/api/v1/devices/{iccid}/deactivate";
        }
        if (timeoutMs <= 0) {
            timeoutMs = 30000;
        }
    }
}
