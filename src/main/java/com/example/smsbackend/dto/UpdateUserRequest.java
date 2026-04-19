package com.example.smsbackend.dto;

import jakarta.validation.constraints.Email;
import java.util.List;

public record UpdateUserRequest(
    String firstName,
    String lastName,
    @Email String email,
    String contactNumber,
    String address,
    Integer userRole,
    Long companyId,
    Long locationId,
    Boolean clearLocation,
    Boolean allCompanyLocations,
    List<Long> managedLocationIds,
    Boolean clearManagedLocations
) {
}
