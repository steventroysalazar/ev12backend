package com.example.smsbackend.service;

import com.example.smsbackend.dto.GeofenceToggleLogRequest;
import com.example.smsbackend.dto.GeofenceToggleLogResponse;
import com.example.smsbackend.entity.AppUser;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.entity.GeofenceToggleLog;
import com.example.smsbackend.entity.UserRole;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.GeofenceToggleLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeofenceToggleLogService {
    private final GeofenceToggleLogRepository geofenceToggleLogRepository;
    private final DeviceRepository deviceRepository;
    private final AppUserRepository appUserRepository;

    public GeofenceToggleLogService(
        GeofenceToggleLogRepository geofenceToggleLogRepository,
        DeviceRepository deviceRepository,
        AppUserRepository appUserRepository
    ) {
        this.geofenceToggleLogRepository = geofenceToggleLogRepository;
        this.deviceRepository = deviceRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public GeofenceToggleLogResponse create(GeofenceToggleLogRequest request) {
        Device device = deviceRepository.findById(request.deviceId())
            .orElseThrow(() -> new IllegalArgumentException("Device not found."));
        AppUser user = appUserRepository.findById(request.actedByUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found."));

        GeofenceToggleLog log = new GeofenceToggleLog();
        log.setDevice(device);
        log.setActedBy(user);
        log.setEnabled(request.enabled());
        log.setConfirmed(request.confirmed());
        log.setCreatedAt(Instant.now());

        return toResponse(geofenceToggleLogRepository.save(log));
    }

    @Transactional(readOnly = true)
    public List<GeofenceToggleLogResponse> listForSuperAdmin(Long requesterUserId) {
        AppUser requester = appUserRepository.findById(requesterUserId)
            .orElseThrow(() -> new IllegalArgumentException("Requester user not found."));

        if (requester.getRole() != UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Only super admin can view geofence toggle logs.");
        }

        return geofenceToggleLogRepository.findTop200ByOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
    }

    private GeofenceToggleLogResponse toResponse(GeofenceToggleLog log) {
        return new GeofenceToggleLogResponse(
            log.getId(),
            log.getDevice().getId(),
            log.getDevice().getExternalDeviceId(),
            log.getActedBy().getId(),
            log.getActedBy().getEmail(),
            log.isEnabled(),
            log.isConfirmed(),
            log.getCreatedAt()
        );
    }
}
