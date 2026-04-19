package com.example.smsbackend.service;

import com.example.smsbackend.dto.CreateLocationRequest;
import com.example.smsbackend.dto.LocationResponse;
import com.example.smsbackend.dto.UpdateLocationRequest;
import com.example.smsbackend.entity.Company;
import com.example.smsbackend.entity.Location;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.CompanyRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final CompanyRepository companyRepository;
    private final AppUserRepository appUserRepository;
    private final DeviceRepository deviceRepository;

    public LocationService(
        LocationRepository locationRepository,
        CompanyRepository companyRepository,
        AppUserRepository appUserRepository,
        DeviceRepository deviceRepository
    ) {
        this.locationRepository = locationRepository;
        this.companyRepository = companyRepository;
        this.appUserRepository = appUserRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public LocationResponse createLocation(CreateLocationRequest request) {
        Company company = companyRepository.findById(request.companyId())
            .orElseThrow(() -> new IllegalArgumentException("Company not found."));

        locationRepository.findByCompanyIdAndNameIgnoreCase(company.getId(), request.name().trim()).ifPresent(existing -> {
            throw new IllegalArgumentException("Location already exists for this company.");
        });

        Location location = new Location();
        location.setName(request.name().trim());
        location.setDetails(request.details() != null ? request.details().trim() : null);
        location.setCompany(company);

        Location saved = locationRepository.save(location);
        return new LocationResponse(saved.getId(), saved.getName(), saved.getDetails(), saved.getCompany().getId(), 0, 0);
    }

    @Transactional
    public LocationResponse updateLocation(Long locationId, UpdateLocationRequest request) {
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new IllegalArgumentException("Location not found."));

        if (request.companyId() != null) {
            Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new IllegalArgumentException("Company not found."));
            location.setCompany(company);
        }

        if (StringUtils.hasText(request.name())) {
            String normalizedName = request.name().trim();
            locationRepository.findByCompanyIdAndNameIgnoreCase(location.getCompany().getId(), normalizedName).ifPresent(existing -> {
                if (!existing.getId().equals(location.getId())) {
                    throw new IllegalArgumentException("Location already exists for this company.");
                }
            });
            location.setName(normalizedName);
        }

        if (request.details() != null) {
            location.setDetails(StringUtils.hasText(request.details()) ? request.details().trim() : null);
        }

        Location saved = locationRepository.save(location);
        return new LocationResponse(
            saved.getId(),
            saved.getName(),
            saved.getDetails(),
            saved.getCompany().getId(),
            appUserRepository.findByLocationId(saved.getId()).size(),
            deviceRepository.countByUserLocationId(saved.getId())
        );
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations() {
        return locationRepository.findAll().stream().map(location -> new LocationResponse(
            location.getId(),
            location.getName(),
            location.getDetails(),
            location.getCompany().getId(),
            appUserRepository.findByLocationId(location.getId()).size(),
            deviceRepository.countByUserLocationId(location.getId())
        )).toList();
    }
}
