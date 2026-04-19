package com.example.smsbackend.dto;

public record UpdateCompanyRequest(
    String name,
    String companyName,
    String details,
    String address,
    String city,
    String state,
    String postalCode,
    String country,
    String phone,
    Boolean isAlarmReceiverIncluded
) {
}
