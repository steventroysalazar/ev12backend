package com.example.smsbackend.dto;

import java.util.List;

public record UserResponse(
    Long id,
    String email,
    String firstName,
    String lastName,
    String contactNumber,
    String address,
    Integer userRole,
    Long companyId,
    Long locationId,
    Long managerId,
    Boolean allCompanyLocations,
    List<Long> managedLocationIds
) {
}
