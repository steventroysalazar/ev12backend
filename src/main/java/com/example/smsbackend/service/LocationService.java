package com.example.smsbackend.service;

import com.example.smsbackend.dto.CreateLocationRequest;
import com.example.smsbackend.dto.LocationResponse;
import com.example.smsbackend.dto.UpdateLocationAlarmReceiverRequest;
import com.example.smsbackend.dto.UpdateLocationRequest;
import com.example.smsbackend.entity.Company;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.entity.Location;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.CompanyRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final ObjectMapper objectMapper;

    public LocationService(
        LocationRepository locationRepository,
        CompanyRepository companyRepository,
        AppUserRepository appUserRepository,
        DeviceRepository deviceRepository,
        ObjectMapper objectMapper
    ) {
        this.locationRepository = locationRepository;
        this.companyRepository = companyRepository;
        this.appUserRepository = appUserRepository;
        this.deviceRepository = deviceRepository;
        this.objectMapper = objectMapper;
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
        location.setGeofenceEnabled(Boolean.TRUE.equals(request.geofenceEnabled()));

        Location saved = locationRepository.save(location);
        return toResponse(saved, 0, 0);
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

        if (request.geofenceEnabled() != null) {
            location.setGeofenceEnabled(request.geofenceEnabled());
        }

        Location saved = locationRepository.save(location);
        return toResponse(saved, appUserRepository.findByLocationId(saved.getId()).size(), deviceRepository.countByUserLocationId(saved.getId()));
    }

    @Transactional
    public LocationResponse updateLocationAlarmReceiverConfig(Long locationId, UpdateLocationAlarmReceiverRequest request) {
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new IllegalArgumentException("Location not found."));

        if (request.accountNumber() != null) {
            String normalizedAccountNumber = trimOrNull(request.accountNumber());
            location.setAlarmReceiverAccountNumber(normalizedAccountNumber);
            syncDeviceBranchAccountNumbers(locationId, normalizedAccountNumber);
        }

        if (request.en() != null) {
            location.setAlarmReceiverEnabled(request.en());
        }

        if (request.users() != null) {
            location.setAlarmReceiverUsersJson(toJson(request.users()));
        }

        if (Boolean.TRUE.equals(request.toggleCompanyAlarmReceiver())) {
            Company company = location.getCompany();
            company.setAlarmReceiverEnabled(false);
            companyRepository.saveAndFlush(company);
            company.setAlarmReceiverEnabled(true);
            companyRepository.save(company);
        }

        Location saved = locationRepository.save(location);
        return toResponse(saved, appUserRepository.findByLocationId(saved.getId()).size(), deviceRepository.countByUserLocationId(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations() {
        return locationRepository.findAll().stream().map(location -> toResponse(
            location,
            appUserRepository.findByLocationId(location.getId()).size(),
            deviceRepository.countByUserLocationId(location.getId())
        )).toList();
    }

    private LocationResponse toResponse(Location location, long usersCount, long devicesCount) {
        return new LocationResponse(
            location.getId(),
            location.getName(),
            location.getDetails(),
            location.getCompany().getId(),
            location.isGeofenceEnabled(),
            toAlarmReceiverConfig(location),
            usersCount,
            devicesCount
        );
    }

    private void syncDeviceBranchAccountNumbers(Long locationId, String accountNumber) {
        List<Device> devices = deviceRepository.findByUserLocationId(locationId);
        devices.forEach(device -> device.setBranchAccountNumber(accountNumber));
        deviceRepository.saveAll(devices);
    }

    private JsonNode toAlarmReceiverConfig(Location location) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("account_number", location.getAlarmReceiverAccountNumber());
        node.put("en", location.isAlarmReceiverEnabled());
        if (StringUtils.hasText(location.getAlarmReceiverUsersJson())) {
            try {
                node.set("users", objectMapper.readTree(location.getAlarmReceiverUsersJson()));
            } catch (JsonProcessingException exception) {
                node.put("users", location.getAlarmReceiverUsersJson());
            }
        } else {
            node.put("users", "");
        }
        return node;
    }

    private String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to store location alarm receiver users.", exception);
        }
    }
}
