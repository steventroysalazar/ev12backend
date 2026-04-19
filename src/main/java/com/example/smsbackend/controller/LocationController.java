package com.example.smsbackend.controller;

import com.example.smsbackend.dto.CreateLocationRequest;
import com.example.smsbackend.dto.LocationResponse;
import com.example.smsbackend.dto.UpdateLocationAlarmReceiverRequest;
import com.example.smsbackend.dto.UpdateLocationRequest;
import com.example.smsbackend.service.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    public ResponseEntity<LocationResponse> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.ok(locationService.createLocation(request));
    }


    @PutMapping("/{locationId}")
    public ResponseEntity<LocationResponse> updateLocation(
        @PathVariable Long locationId,
        @Valid @RequestBody UpdateLocationRequest request
    ) {
        return ResponseEntity.ok(locationService.updateLocation(locationId, request));
    }

    @PutMapping("/{locationId}/alarm-receiver")
    public ResponseEntity<LocationResponse> updateLocationAlarmReceiver(
        @PathVariable Long locationId,
        @RequestBody UpdateLocationAlarmReceiverRequest request
    ) {
        return ResponseEntity.ok(locationService.updateLocationAlarmReceiverConfig(locationId, request));
    }

    @GetMapping
    public ResponseEntity<List<LocationResponse>> listLocations() {
        return ResponseEntity.ok(locationService.listLocations());
    }
}
