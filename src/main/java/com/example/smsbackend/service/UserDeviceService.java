package com.example.smsbackend.service;

import com.example.smsbackend.dto.CreateDeviceRequest;
import com.example.smsbackend.dto.DeviceProtocolSettings;
import com.example.smsbackend.dto.DeviceResponse;
import com.example.smsbackend.dto.UpdateDeviceRequest;
import com.example.smsbackend.dto.UpdateUserRequest;
import com.example.smsbackend.dto.UserResponse;
import com.example.smsbackend.entity.AppUser;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.entity.Location;
import com.example.smsbackend.entity.UserRole;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserDeviceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDeviceService.class);

    public static final String CONFIG_STATUS_IDLE = "IDLE";
    public static final String CONFIG_STATUS_PENDING = "PENDING";
    public static final String CONFIG_STATUS_APPLIED = "APPLIED";
    public static final Duration CONFIG_RESEND_COOLDOWN = Duration.ofMinutes(5);

    private final AppUserRepository appUserRepository;
    private final DeviceRepository deviceRepository;
    private final LocationRepository locationRepository;
    private final ObjectMapper objectMapper;

    public UserDeviceService(
        AppUserRepository appUserRepository,
        DeviceRepository deviceRepository,
        LocationRepository locationRepository,
        ObjectMapper objectMapper
    ) {
        this.appUserRepository = appUserRepository;
        this.deviceRepository = deviceRepository;
        this.locationRepository = locationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return appUserRepository.findAll().stream()
            .map(AuthService::toUserResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsersByManager(Long managerId) {
        AppUser manager = appUserRepository.findById(managerId)
            .orElseThrow(() -> new IllegalArgumentException("Manager not found."));
        if (manager.getRole() != UserRole.MANAGER) {
            throw new IllegalArgumentException("Provided id is not a manager.");
        }
        return appUserRepository.findByManagerId(managerId).stream()
            .map(AuthService::toUserResponse)
            .toList();
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (StringUtils.hasText(request.firstName())) {
            user.setFirstName(request.firstName().trim());
        }

        if (StringUtils.hasText(request.lastName())) {
            user.setLastName(request.lastName().trim());
        }

        if (StringUtils.hasText(request.email())) {
            String normalizedEmail = request.email().trim().toLowerCase();
            appUserRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId())) {
                    throw new IllegalArgumentException("User with that email already exists.");
                }
            });
            user.setEmail(normalizedEmail);
        }

        if (request.contactNumber() != null) {
            user.setContactNumber(trimOrNull(request.contactNumber()));
        }

        if (request.address() != null) {
            user.setAddress(trimOrNull(request.address()));
        }

        if (request.userRole() != null) {
            user.setRole(mapRole(request.userRole()));
        }

        if (Boolean.TRUE.equals(request.clearLocation()) && request.locationId() != null) {
            throw new IllegalArgumentException("Provide locationId or clearLocation=true, not both.");
        }

        if (Boolean.TRUE.equals(request.clearLocation())) {
            user.setLocation(null);
        } else if (request.locationId() != null) {
            Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new IllegalArgumentException("Location not found."));
            user.setLocation(location);
        }

        if (Boolean.TRUE.equals(request.clearManager()) && request.managerId() != null) {
            throw new IllegalArgumentException("Provide managerId or clearManager=true, not both.");
        }

        if (Boolean.TRUE.equals(request.clearManager())) {
            user.setManager(null);
        } else if (request.managerId() != null) {
            AppUser manager = appUserRepository.findById(request.managerId())
                .orElseThrow(() -> new IllegalArgumentException("Manager not found."));
            if (manager.getRole() != UserRole.MANAGER) {
                throw new IllegalArgumentException("Assigned manager must have role 2 (MANAGER).");
            }
            user.setManager(manager);
        }

        if (user.getRole() == UserRole.USER && user.getManager() == null) {
            throw new IllegalArgumentException("Role 3 user must be assigned to a manager (role 2).");
        }

        AppUser saved = appUserRepository.save(user);
        return AuthService.toUserResponse(saved);
    }

    @Transactional
    public DeviceResponse createDevice(Long userId, CreateDeviceRequest request) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));

        Device device = new Device();
        device.setUser(user);
        device.setName(request.name().trim());
        device.setPhoneNumber(request.phoneNumber().trim());
        device.setExternalDeviceId(trimOrNull(request.externalDeviceId()));
        device.setProtocolConfig(toProtocolSettingsJson(null));
        Device saved = deviceRepository.save(device);
        return toDeviceResponse(saved);
    }

    @Transactional
    public DeviceResponse updateDevice(Long deviceId, UpdateDeviceRequest request) {
        Device device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found."));

        if (StringUtils.hasText(request.name())) {
            device.setName(request.name().trim());
        }

        if (StringUtils.hasText(request.phoneNumber())) {
            device.setPhoneNumber(request.phoneNumber().trim());
        }

        if (request.externalDeviceId() != null) {
            device.setExternalDeviceId(trimOrNull(request.externalDeviceId()));
        }

        if (request.protocolSettings() != null) {
            device.setProtocolConfig(toProtocolSettingsJson(request.protocolSettings()));
        }

        if (request.userId() != null) {
            AppUser user = appUserRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
            device.setUser(user);
        }

        Device saved = deviceRepository.save(device);
        return toDeviceResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listUserDevices(Long userId) {
        if (!appUserRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found.");
        }
        return deviceRepository.findByUserIdOrderByNameAsc(userId).stream()
            .map(this::toDeviceResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listDevicesByLocation(Long locationId) {
        return deviceRepository.findByUserLocationId(locationId).stream()
            .map(this::toDeviceResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listAllDevices() {
        return deviceRepository.findAll().stream()
            .map(this::toDeviceResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public Device getDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found."));
    }

    private UserRole mapRole(Integer code) {
        return switch (code) {
            case 1 -> UserRole.SUPER_ADMIN;
            case 2 -> UserRole.MANAGER;
            case 3 -> UserRole.USER;
            default -> throw new IllegalArgumentException("Invalid userRole. Use 1=super admin, 2=manager, 3=user.");
        };
    }

    private String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private DeviceResponse toDeviceResponse(Device device) {
        return new DeviceResponse(
            device.getId(),
            device.getUser().getId(),
            device.getName(),
            device.getPhoneNumber(),
            device.getExternalDeviceId(),
            device.getAlarmCode(),
            fromProtocolSettingsJson(device.getProtocolConfig()),
            device.getConfigStatus(),
            device.getConfigLastSentAt(),
            device.getConfigAppliedAt()
        );
    }

    public void saveDeviceProtocolSettings(Device device, DeviceProtocolSettings settings) {
        device.setProtocolConfig(toProtocolSettingsJson(settings));
        deviceRepository.save(device);
    }

    public void markDeviceConfigPending(Device device, String commandPreview, Instant sentAt) {
        device.setConfigStatus(CONFIG_STATUS_PENDING);
        device.setConfigCommandPreview(commandPreview);
        device.setConfigLastSentAt(sentAt);
        device.setConfigAppliedAt(null);
        deviceRepository.save(device);
    }

    public void markDeviceConfigApplied(Device device, Instant appliedAt) {
        device.setConfigStatus(CONFIG_STATUS_APPLIED);
        device.setConfigAppliedAt(appliedAt);
        deviceRepository.save(device);
    }

    public Instant nextResendAt(Device device) {
        if (device.getConfigLastSentAt() == null) {
            return Instant.now();
        }
        return device.getConfigLastSentAt().plus(CONFIG_RESEND_COOLDOWN);
    }

    public boolean canResend(Device device, Instant now) {
        if (!CONFIG_STATUS_PENDING.equals(device.getConfigStatus())) {
            return false;
        }
        if (device.getConfigLastSentAt() == null) {
            return true;
        }
        return !now.isBefore(nextResendAt(device));
    }

    private String toProtocolSettingsJson(DeviceProtocolSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to store protocol settings.", exception);
        }
    }

    private DeviceProtocolSettings fromProtocolSettingsJson(String value) {
        if (!StringUtils.hasText(value) || "null".equals(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, DeviceProtocolSettings.class);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to read protocol settings JSON for device payload. Returning null settings.", exception);
            return null;
        }
    }
}
