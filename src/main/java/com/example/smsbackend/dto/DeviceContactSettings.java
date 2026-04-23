package com.example.smsbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record DeviceContactSettings(
    @JsonAlias("slot")
    Integer slot,
    @JsonAlias("sms_enabled")
    Boolean smsEnabled,
    @JsonAlias("call_enabled")
    Boolean callEnabled,
    @JsonAlias("phone")
    String phone,
    @JsonAlias("name")
    String name
) {
}
