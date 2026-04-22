package com.example.smsbackend.service;

import com.example.smsbackend.dto.CompanyLookupResponse;
import com.example.smsbackend.dto.CreateDeviceRequest;
import com.example.smsbackend.dto.DeviceAlarmLogResponse;
import com.example.smsbackend.dto.DeviceLocationBreadcrumbResponse;
import com.example.smsbackend.dto.DeviceProtocolSettings;
import com.example.smsbackend.dto.DeviceResponse;
import com.example.smsbackend.dto.LocationLookupResponse;
import com.example.smsbackend.dto.SimBulkActivationRequest;
import com.example.smsbackend.dto.SimBulkActivationResult;
import com.example.smsbackend.dto.SimStatusResponse;
import com.example.smsbackend.dto.UpdateDeviceRequest;
import com.example.smsbackend.dto.UpdateUserRequest;
import com.example.smsbackend.dto.UserLookupResponse;
import com.example.smsbackend.dto.UserResponse;
import com.example.smsbackend.entity.AppUser;
import com.example.smsbackend.entity.Company;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.entity.Location;
import com.example.smsbackend.entity.UserRole;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.CompanyRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;
    private final DeviceTelemetryLogService deviceTelemetryLogService;
    private final DeviceImeiService deviceImeiService;
    private final CiscoControlCenterService ciscoControlCenterService;

    public UserDeviceService(
        AppUserRepository appUserRepository,
        DeviceRepository deviceRepository,
        LocationRepository locationRepository,
        CompanyRepository companyRepository,
        ObjectMapper objectMapper,
        DeviceTelemetryLogService deviceTelemetryLogService,
        DeviceImeiService deviceImeiService,
        CiscoControlCenterService ciscoControlCenterService
    ) {
        this.appUserRepository = appUserRepository;
        this.deviceRepository = deviceRepository;
        this.locationRepository = locationRepository;
        this.companyRepository = companyRepository;
        this.objectMapper = objectMapper;
        this.deviceTelemetryLogService = deviceTelemetryLogService;
        this.deviceImeiService = deviceImeiService;
        this.ciscoControlCenterService = ciscoControlCenterService;
    }

    @Transactional(readOnly = true)
    public List<UserLookupResponse> listCompanyAdminsLookup() {
        return listUsersLookupByRole(UserRole.COMPANY_ADMIN);
    }

    @Transactional(readOnly = true)
    public List<UserLookupResponse> listPortalUsersLookup() {
        return listUsersLookupByRole(UserRole.PORTAL_USER);
    }

    @Transactional(readOnly = true)
    public List<UserLookupResponse> listSuperAdminsLookup() {
        return listUsersLookupByRole(UserRole.SUPER_ADMIN);
    }

    @Transactional(readOnly = true)
    public List<UserLookupResponse> listMobileUsersLookup() {
        return listUsersLookupByRole(UserRole.MOBILE_APP_USER);
    }

    @Transactional(readOnly = true)
    public List<UserLookupResponse> listUsersLookupByLocation(Long locationId) {
        if (!locationRepository.existsById(locationId)) {
            throw new IllegalArgumentException("Location not found.");
        }
        return appUserRepository.findByLocationIdOrderByFirstNameAscLastNameAsc(locationId).stream()
            .map(user -> new UserLookupResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().getCode()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationLookupResponse> listLocationsLookup() {
        return locationRepository.findAllByOrderByNameAsc().stream()
            .map(location -> new LocationLookupResponse(location.getId(), location.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyLookupResponse> listCompaniesLookup() {
        return companyRepository.findAllByOrderByNameAsc().stream()
            .map(company -> new CompanyLookupResponse(company.getId(), company.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return appUserRepository.findAll().stream()
            .map(AuthService::toUserResponse)
            .toList();
    }
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
        return AuthService.toUserResponse(user);
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

        if (request.companyId() != null) {
            Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new IllegalArgumentException("Company not found."));
            user.setCompany(company);
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

        if (request.allCompanyLocations() != null) {
            user.setAllCompanyLocations(request.allCompanyLocations());
        }

        if (Boolean.TRUE.equals(request.clearManagedLocations()) && request.managedLocationIds() != null) {
            throw new IllegalArgumentException("Provide managedLocationIds or clearManagedLocations=true, not both.");
        }

        if (Boolean.TRUE.equals(request.clearManagedLocations())) {
            user.getManagedLocations().clear();
        } else if (request.managedLocationIds() != null) {
            Set<Location> locations = new HashSet<>(locationRepository.findAllById(request.managedLocationIds()));
            if (locations.size() != request.managedLocationIds().size()) {
                throw new IllegalArgumentException("One or more managedLocationIds are invalid.");
            }
            user.setManagedLocations(locations);
        }

        validateUserRelationships(user);

        AppUser saved = appUserRepository.save(user);
        return AuthService.toUserResponse(saved);
    }

    private void validateUserRelationships(AppUser user) {
        if (user.getRole() != UserRole.SUPER_ADMIN && user.getCompany() == null) {
            throw new IllegalArgumentException("Roles 2, 3, and 4 must belong to a company.");
        }

        if (user.getLocation() != null && user.getCompany() != null
            && !user.getLocation().getCompany().getId().equals(user.getCompany().getId())) {
            throw new IllegalArgumentException("Selected location does not belong to user's company.");
        }

        if (user.getRole() == UserRole.COMPANY_ADMIN) {
            if (!user.isAllCompanyLocations() && user.getManagedLocations().isEmpty()) {
                throw new IllegalArgumentException("Company admin with allCompanyLocations=false must include managedLocationIds.");
            }
            if (user.getCompany() != null) {
                boolean mismatch = user.getManagedLocations().stream()
                    .anyMatch(location -> !location.getCompany().getId().equals(user.getCompany().getId()));
                if (mismatch) {
                    throw new IllegalArgumentException("All managed locations must belong to the same company.");
                }
            }
        } else {
            user.setAllCompanyLocations(true);
            user.getManagedLocations().clear();
        }
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
        device.setSimIccid(trimOrNull(request.simIccid()));
        device.setProtocolConfig(toProtocolSettingsJson(null));
        Device saved = deviceRepository.save(device);
        try {
            deviceImeiService.requestImei(saved, null);
        } catch (Exception exception) {
            LOGGER.warn("Device created but failed to send IMEI request SMS for deviceId={}: {}", saved.getId(), exception.getMessage());
        }
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

        if (request.simIccid() != null) {
            device.setSimIccid(trimOrNull(request.simIccid()));
        }

        if (request.protocolSettings() != null) {
            device.setProtocolConfig(toProtocolSettingsJson(request.protocolSettings()));
        }

        if (request.userId() != null) {
            AppUser user = appUserRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
            device.setUser(user);
        }

        if (Boolean.TRUE.equals(request.clearLocation()) && request.locationId() != null) {
            throw new IllegalArgumentException("Provide locationId or clearLocation=true, not both.");
        }

        if (Boolean.TRUE.equals(request.clearLocation())) {
            device.getUser().setLocation(null);
        } else if (request.locationId() != null) {
            Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new IllegalArgumentException("Location not found."));
            device.getUser().setLocation(location);
        }

        if (request.alarmCodeProvided()) {
            String nextAlarmCode = trimOrNull(request.alarmCode());
            if (nextAlarmCode == null && device.getAlarmCode() != null) {
                Instant cancelledAt = request.alarmCancelledAtProvided() && request.alarmCancelledAt() != null
                    ? request.alarmCancelledAt()
                    : Instant.now();
                device.setAlarmCancelledAt(cancelledAt);
                device.setAlarmTriggeredAt(null);
                deviceTelemetryLogService.logAlarmEvent(
                    device,
                    "ALARM_CANCELLED",
                    "MANUAL",
                    null,
                    cancelledAt,
                    "Alarm cancelled from device update API"
                );
            }
            if (nextAlarmCode != null && !nextAlarmCode.equalsIgnoreCase(String.valueOf(device.getAlarmCode()))) {
                device.setAlarmTriggeredAt(Instant.now());
                device.setAlarmCancelledAt(null);
            }
            device.setAlarmCode(nextAlarmCode);
        }

        if (request.alarmCancelledAtProvided()) {
            device.setAlarmCancelledAt(request.alarmCancelledAt());
        }

        Device saved = deviceRepository.save(device);
        try {
            deviceImeiService.requestImei(saved, null);
        } catch (Exception exception) {
            LOGGER.warn("Device created but failed to send IMEI request SMS for deviceId={}: {}", saved.getId(), exception.getMessage());
        }
        return toDeviceResponse(saved);
    }

    @Transactional
    public SimStatusResponse refreshSimStatus(Long deviceId) {
        Device device = getDevice(deviceId);
        if (!StringUtils.hasText(device.getSimIccid())) {
            throw new IllegalArgumentException("Device does not have simIccid. Update device.simIccid first.");
        }
        CiscoControlCenterService.CiscoDeviceDetails details = ciscoControlCenterService.fetchDeviceDetails(device.getSimIccid());
        applySimDetails(device, details);
        deviceRepository.save(device);
        return toSimStatusResponse(device);
    }

    @Transactional
    public SimStatusResponse setSimActivation(Long deviceId, boolean activate) {
        Device device = getDevice(deviceId);
        if (!StringUtils.hasText(device.getSimIccid())) {
            throw new IllegalArgumentException("Device does not have simIccid. Update device.simIccid first.");
        }
        if (activate) {
            ciscoControlCenterService.activateSim(device.getSimIccid());
        } else {
            ciscoControlCenterService.deactivateSim(device.getSimIccid());
        }
        try {
            CiscoControlCenterService.CiscoDeviceDetails details = ciscoControlCenterService.fetchDeviceDetails(device.getSimIccid());
            applySimDetails(device, details);
        } catch (Exception ignored) {
            device.setSimActivated(activate);
            device.setSimStatus(activate ? "ACTIVATED" : "DEACTIVATED");
            device.setSimStatusUpdatedAt(Instant.now());
        }
        deviceRepository.save(device);
        return toSimStatusResponse(device);
    }

    @Transactional
    public List<SimBulkActivationResult> bulkSetSimActivation(SimBulkActivationRequest request) {
        return request.deviceIds().stream()
            .map(deviceId -> {
                try {
                    SimStatusResponse status = setSimActivation(deviceId, request.activate());
                    return new SimBulkActivationResult(deviceId, true, null, status);
                } catch (Exception exception) {
                    return new SimBulkActivationResult(deviceId, false, exception.getMessage(), null);
                }
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceAlarmLogResponse> listDeviceAlarmLogs(Long deviceId) {
        getDevice(deviceId);
        return deviceTelemetryLogService.listAlarmLogs(deviceId);
    }

    @Transactional(readOnly = true)
    public List<DeviceLocationBreadcrumbResponse> listDeviceLocationBreadcrumbs(Long deviceId) {
        getDevice(deviceId);
        return deviceTelemetryLogService.listLocationBreadcrumbs(deviceId);
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
    public DeviceResponse getDeviceById(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found."));
        return toDeviceResponse(device);
    }

    @Transactional(readOnly = true)
    public Device getDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found."));
    }

    private List<UserLookupResponse> listUsersLookupByRole(UserRole role) {
        return appUserRepository.findByRoleOrderByFirstNameAscLastNameAsc(role).stream()
            .map(user -> new UserLookupResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().getCode()
            ))
            .toList();
    }

    private UserRole mapRole(Integer code) {
        return switch (code) {
            case 1 -> UserRole.SUPER_ADMIN;
            case 2 -> UserRole.COMPANY_ADMIN;
            case 3 -> UserRole.PORTAL_USER;
            case 4 -> UserRole.MOBILE_APP_USER;
            default -> throw new IllegalArgumentException("Invalid userRole. Use 1=super admin, 2=company admin, 3=portal user, 4=mobile app user.");
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
            device.getUser().getCompany() != null ? device.getUser().getCompany().getId() : null,
            device.getName(),
            device.getPhoneNumber(),
            device.getExternalDeviceId(),
            device.getSimIccid(),
            device.getSimStatus(),
            device.isSimActivated(),
            device.getSimStatusUpdatedAt(),
            device.getAlarmCode(),
            device.getAlarmTriggeredAt(),
            device.getAlarmCancelledAt(),
            device.getLastPowerOnAt(),
            device.getLastPowerOffAt(),
            device.getLastDisconnectedAt(),
            device.getLatitude(),
            device.getLongitude(),
            device.getLocationUpdatedAt(),
            device.getBranchAccountNumber(),
            fromProtocolSettingsJson(device.getProtocolConfig()),
            device.getConfigStatus(),
            device.getConfigLastSentAt(),
            device.getConfigAppliedAt()
        );
    }

    private void applySimDetails(Device device, CiscoControlCenterService.CiscoDeviceDetails details) {
        device.setSimIccid(trimOrNull(details.iccid()));
        if (StringUtils.hasText(details.msisdn())) {
            device.setPhoneNumber(details.msisdn().trim());
        }
        device.setSimStatus(trimOrNull(details.status()));
        device.setSimActivated(details.activated());
        device.setSimStatusUpdatedAt(Instant.now());
    }

    private SimStatusResponse toSimStatusResponse(Device device) {
        return new SimStatusResponse(
            device.getId(),
            device.getSimIccid(),
            device.getPhoneNumber(),
            device.getSimStatus(),
            device.isSimActivated(),
            device.getSimStatusUpdatedAt()
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
            LOGGER.warn("Unable to parse protocol settings JSON for device response: {}", exception.getMessage());
            return null;
        }
    }
}
