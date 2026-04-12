package com.example.smsbackend.controller;

import com.example.smsbackend.dto.AlertLogFiltersResponse;
import com.example.smsbackend.dto.LocationLookupResponse;
import com.example.smsbackend.dto.UserLookupResponse;
import com.example.smsbackend.service.DeviceTelemetryLogService;
import com.example.smsbackend.service.UserDeviceService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/managers")
    public ResponseEntity<List<UserLookupResponse>> listManagers() {
        return ResponseEntity.ok(userDeviceService.listManagersLookup());
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserLookupResponse>> listUsersRoleLookup() {
        return ResponseEntity.ok(userDeviceService.listRoleUsersLookup());
    }

    @GetMapping("/super-admins")
    public ResponseEntity<List<UserLookupResponse>> listSuperAdmins() {
        return ResponseEntity.ok(userDeviceService.listSuperAdminsLookup());
    }

    @GetMapping("/locations")
    public ResponseEntity<List<LocationLookupResponse>> listLocations() {
        return ResponseEntity.ok(userDeviceService.listLocationsLookup());
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
