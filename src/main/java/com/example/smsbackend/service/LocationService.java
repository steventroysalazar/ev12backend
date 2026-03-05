package com.example.smsbackend.service;

import com.example.smsbackend.dto.CreateLocationRequest;
import com.example.smsbackend.dto.LocationResponse;
import com.example.smsbackend.entity.Location;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final AppUserRepository appUserRepository;
    private final DeviceRepository deviceRepository;

    public LocationService(
        LocationRepository locationRepository,
        AppUserRepository appUserRepository,
        DeviceRepository deviceRepository
    ) {
        this.locationRepository = locationRepository;
        this.appUserRepository = appUserRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public LocationResponse createLocation(CreateLocationRequest request) {
        locationRepository.findByNameIgnoreCase(request.name().trim()).ifPresent(existing -> {
            throw new IllegalArgumentException("Location already exists.");
        });

        Location location = new Location();
        location.setName(request.name().trim());
        location.setDetails(request.details() != null ? request.details().trim() : null);

        Location saved = locationRepository.save(location);
        return new LocationResponse(saved.getId(), saved.getName(), saved.getDetails(), 0, 0);
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations() {
        return locationRepository.findAll().stream().map(location -> new LocationResponse(
            location.getId(),
            location.getName(),
            location.getDetails(),
            appUserRepository.findByLocationId(location.getId()).size(),
            deviceRepository.countByUserLocationId(location.getId())
        )).toList();
    }
}
