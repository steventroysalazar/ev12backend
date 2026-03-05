package com.example.smsbackend.dto;

import java.util.List;

public record SendConfigResponse(
    boolean success,
    Long deviceId,
    String deviceNumber,
    String commandPreview,
    List<SentMessageResponse> messages
) {
}
