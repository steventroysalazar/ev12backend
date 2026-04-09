package com.example.smsbackend.controller;

import com.example.smsbackend.dto.CreateDeviceForUserRequest;
import com.example.smsbackend.dto.DeviceAlarmLogResponse;
import com.example.smsbackend.dto.DeviceLocationBreadcrumbResponse;
import com.example.smsbackend.dto.CreateDeviceRequest;
import com.example.smsbackend.dto.DeviceResponse;
import com.example.smsbackend.dto.UpdateDeviceRequest;
import com.example.smsbackend.dto.UpdateUserRequest;
import com.example.smsbackend.dto.UserResponse;
import com.example.smsbackend.service.UserDeviceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserDeviceService userDeviceService;

    public UserController(UserDeviceService userDeviceService) {
        this.userDeviceService = userDeviceService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> listUsers(
        @RequestParam(required = false) Long managerId
    ) {
        List<UserResponse> response = managerId == null
            ? userDeviceService.listUsers()
            : userDeviceService.listUsersByManager(managerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userDeviceService.getUserById(userId));
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable Long userId,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userDeviceService.updateUser(userId, request));
    }

    @PostMapping("/users/{userId}/devices")
    public ResponseEntity<DeviceResponse> createDevice(
        @PathVariable Long userId,
        @Valid @RequestBody CreateDeviceRequest request
    ) {
        return ResponseEntity.ok(userDeviceService.createDevice(userId, request));
    }


    @PostMapping("/devices")
    public ResponseEntity<DeviceResponse> createDeviceForUser(@Valid @RequestBody CreateDeviceForUserRequest request) {
        CreateDeviceRequest createDeviceRequest = new CreateDeviceRequest(request.name(), request.phoneNumber(), request.externalDeviceId());
        return ResponseEntity.ok(userDeviceService.createDevice(request.userId(), createDeviceRequest));
    }


    @PutMapping("/devices/{deviceId}")
    public ResponseEntity<DeviceResponse> updateDevice(
        @PathVariable Long deviceId,
        @Valid @RequestBody UpdateDeviceRequest request
    ) {
        return ResponseEntity.ok(userDeviceService.updateDevice(deviceId, request));
    }


    @PatchMapping("/devices/{deviceId}")
    public ResponseEntity<DeviceResponse> patchDevice(
        @PathVariable Long deviceId,
        @RequestBody UpdateDeviceRequest request
    ) {
        return ResponseEntity.ok(userDeviceService.updateDevice(deviceId, request));
    }

    @GetMapping("/devices/{deviceId}")
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable Long deviceId) {
        return ResponseEntity.ok(userDeviceService.getDeviceById(deviceId));
    }

    @GetMapping("/devices")
    public ResponseEntity<List<DeviceResponse>> listDevices() {
        return ResponseEntity.ok(userDeviceService.listAllDevices());
    }

    @GetMapping("/devices/{deviceId}/alarm-logs")
    public ResponseEntity<List<DeviceAlarmLogResponse>> listDeviceAlarmLogs(@PathVariable Long deviceId) {
        return ResponseEntity.ok(userDeviceService.listDeviceAlarmLogs(deviceId));
    }

    @GetMapping("/devices/{deviceId}/location-breadcrumbs")
    public ResponseEntity<List<DeviceLocationBreadcrumbResponse>> listDeviceLocationBreadcrumbs(@PathVariable Long deviceId) {
        return ResponseEntity.ok(userDeviceService.listDeviceLocationBreadcrumbs(deviceId));
    }

    @GetMapping("/users/{userId}/devices")
    public ResponseEntity<List<DeviceResponse>> listUserDevices(@PathVariable Long userId) {
        List<DeviceResponse> response = userDeviceService.listUserDevices(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/locations/{locationId}/devices")
    public ResponseEntity<List<DeviceResponse>> listLocationDevices(@PathVariable Long locationId) {
        List<DeviceResponse> response = userDeviceService.listDevicesByLocation(locationId);
        return ResponseEntity.ok(response);
    }
}
