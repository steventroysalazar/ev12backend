package com.example.smsbackend.controller;

import com.example.smsbackend.dto.AlertLogFiltersResponse;
import com.example.smsbackend.dto.CompanyLookupResponse;
import com.example.smsbackend.dto.LocationLookupResponse;
import com.example.smsbackend.dto.UserLookupResponse;
import com.example.smsbackend.service.DeviceTelemetryLogService;
import com.example.smsbackend.service.UserDeviceService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lookups")
public class LookupController {

    private final UserDeviceService userDeviceService;
    private final DeviceTelemetryLogService deviceTelemetryLogService;

    public LookupController(UserDeviceService userDeviceService, DeviceTelemetryLogService deviceTelemetryLogService) {
        this.userDeviceService = userDeviceService;
        this.deviceTelemetryLogService = deviceTelemetryLogService;
    }

    @GetMapping("/company-admins")
    public ResponseEntity<List<UserLookupResponse>> listCompanyAdmins() {
        return ResponseEntity.ok(userDeviceService.listCompanyAdminsLookup());
    }

    @GetMapping("/portal-users")
    public ResponseEntity<List<UserLookupResponse>> listPortalUsers() {
        return ResponseEntity.ok(userDeviceService.listPortalUsersLookup());
    }

    @GetMapping("/mobile-users")
    public ResponseEntity<List<UserLookupResponse>> listMobileUsers() {
        return ResponseEntity.ok(userDeviceService.listMobileUsersLookup());
    }

    @GetMapping("/super-admins")
    public ResponseEntity<List<UserLookupResponse>> listSuperAdmins() {
        return ResponseEntity.ok(userDeviceService.listSuperAdminsLookup());
    }

    @GetMapping("/companies")
    public ResponseEntity<List<CompanyLookupResponse>> listCompanies() {
        return ResponseEntity.ok(userDeviceService.listCompaniesLookup());
    }

    @GetMapping("/locations")
    public ResponseEntity<List<LocationLookupResponse>> listLocations() {
        return ResponseEntity.ok(userDeviceService.listLocationsLookup());
    }

    @GetMapping("/locations/{locationId}/users")
    public ResponseEntity<List<UserLookupResponse>> listUsersByLocation(@PathVariable Long locationId) {
        return ResponseEntity.ok(userDeviceService.listUsersLookupByLocation(locationId));
    }


    @GetMapping("/alerts")
    public ResponseEntity<List<String>> listActiveAlerts() {
        return ResponseEntity.ok(deviceTelemetryLogService.listActiveAlertsLookup());
    }

    @GetMapping("/alert-logs")
    public ResponseEntity<AlertLogFiltersResponse> listAlertLogFilters() {
        return ResponseEntity.ok(deviceTelemetryLogService.listAlertLogFiltersLookup());
    }
}
