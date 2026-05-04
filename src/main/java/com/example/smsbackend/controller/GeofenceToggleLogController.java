package com.example.smsbackend.controller;

import com.example.smsbackend.dto.GeofenceToggleLogRequest;
import com.example.smsbackend.dto.GeofenceToggleLogResponse;
import com.example.smsbackend.service.GeofenceToggleLogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geofence-toggle-logs")
@CrossOrigin(
    originPatterns = {
        "https://ev12frontend-dbdk.vercel.app",
        "https://*.vercel.app",
        "http://localhost:*",
        "https://localhost:*"
    },
    allowCredentials = "true"
)
public class GeofenceToggleLogController {

    private final GeofenceToggleLogService geofenceToggleLogService;

    public GeofenceToggleLogController(GeofenceToggleLogService geofenceToggleLogService) {
        this.geofenceToggleLogService = geofenceToggleLogService;
    }

    @PostMapping
    public GeofenceToggleLogResponse create(@Valid @RequestBody GeofenceToggleLogRequest request) {
        return geofenceToggleLogService.create(request);
    }

    @GetMapping
    public List<GeofenceToggleLogResponse> list(@RequestParam("requester_user_id") Long requesterUserId) {
        return geofenceToggleLogService.listForSuperAdmin(requesterUserId);
    }
}
