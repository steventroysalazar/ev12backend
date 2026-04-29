package com.example.smsbackend.service;

import com.example.smsbackend.dto.AuthResponse;
import com.example.smsbackend.dto.CreateUserRequest;
import com.example.smsbackend.dto.FcmTokenResponse;
import com.example.smsbackend.dto.FcmTokenDetailsResponse;
import com.example.smsbackend.dto.LoginAuditContext;
import com.example.smsbackend.dto.LoginContextResponse;
import com.example.smsbackend.dto.LoginLogResponse;
import com.example.smsbackend.dto.LoginRequest;
import com.example.smsbackend.dto.LogoutRequest;
import com.example.smsbackend.dto.LogoutResponse;
import com.example.smsbackend.dto.UpsertFcmTokenRequest;
import com.example.smsbackend.dto.UserResponse;
import com.example.smsbackend.entity.AppUser;
import com.example.smsbackend.entity.Company;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.entity.LoginLog;
import com.example.smsbackend.entity.Location;
import com.example.smsbackend.entity.UserRole;
import com.example.smsbackend.entity.UserDevice;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.CompanyRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LoginLogRepository;
import com.example.smsbackend.repository.LocationRepository;
import com.example.smsbackend.repository.UserDeviceRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final DeviceRepository deviceRepository;
    private final LoginLogRepository loginLogRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
        AppUserRepository appUserRepository,
        CompanyRepository companyRepository,
        LocationRepository locationRepository,
        DeviceRepository deviceRepository,
        LoginLogRepository loginLogRepository,
        UserDeviceRepository userDeviceRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.deviceRepository = deviceRepository;
        this.loginLogRepository = loginLogRepository;
        this.userDeviceRepository = userDeviceRepository;
    }

    @Transactional
    public UserResponse register(CreateUserRequest request) {
        appUserRepository.findByEmailIgnoreCase(request.email().trim()).ifPresent(existing -> {
            throw new IllegalArgumentException("User with that email already exists.");
        });

        UserRole role = mapRole(request.userRole());

        AppUser user = new AppUser();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setContactNumber(trimOrNull(request.contactNumber()));
        user.setAddress(trimOrNull(request.address()));
        user.setRole(role);

        assignCompany(user, request.companyId());
        assignLocation(user, request.locationId());

        boolean allCompanyLocations = request.allCompanyLocations() == null || request.allCompanyLocations();
        user.setAllCompanyLocations(allCompanyLocations);
        syncManagedLocations(user, request.managedLocationIds());

        validateUserRelationships(user);

        AppUser saved = appUserRepository.save(user);

        if (request.device() != null) {
            String externalDeviceId = request.device().deviceId().trim();
            deviceRepository.findByExternalDeviceId(externalDeviceId).ifPresent(existing -> {
                throw new IllegalArgumentException("Device with that deviceId already exists.");
            });

            Device device = new Device();
            device.setUser(saved);
            device.setName(request.device().name().trim());
            device.setPhoneNumber(request.device().phoneNumber().trim());
            device.setExternalDeviceId(externalDeviceId);
            deviceRepository.save(device);
        }

        return toUserResponse(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, LoginAuditContext auditContext) {
        String identifier = resolveLoginIdentifier(request);
        AppUser user = appUserRepository.findByEmailIgnoreCase(identifier)
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (request.password().equals(user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(request.password()));
                appUserRepository.save(user);
            } else {
                throw new IllegalArgumentException("Invalid email or password.");
            }
        }

        Instant loggedAt = Instant.now();
        logAuthEvent("LOGIN", user, request.grantType(), request.scope(), request.osType(), request.apiVersion(), request.deviceId(), identifier, auditContext, loggedAt);
        upsertUserDevice(user, request, loggedAt);

        String tokenRaw = user.getEmail() + ":" + Instant.now().toEpochMilli();
        String token = Base64.getEncoder().encodeToString(tokenRaw.getBytes(StandardCharsets.UTF_8));
        return new AuthResponse(
            true,
            token,
            toUserResponse(user),
            new LoginContextResponse(
                identifier,
                trimOrNull(request.grantType()),
                trimOrNull(request.scope()),
                trimOrNull(request.osType()),
                trimOrNull(request.apiVersion()),
                trimOrNull(request.deviceId()),
                trimOrNull(auditContext.ipAddress()),
                trimOrNull(auditContext.userAgent()),
                loggedAt
            )
        );
    }

    @Transactional
    public FcmTokenResponse upsertFcmToken(UpsertFcmTokenRequest request) {
        AppUser user = appUserRepository.findById(request.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found."));

        String deviceId = trimOrNull(request.deviceId());
        if (deviceId == null) {
            throw new IllegalArgumentException("deviceId is required for fcm-token updates.");
        }
        UserDevice userDevice = userDeviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId)
            .orElseThrow(() -> new IllegalArgumentException("User device not found for this userId and deviceId."));
        userDevice.setFcmToken(request.fcmToken().trim());
        userDeviceRepository.save(userDevice);

        user.setFcmTokenUpdatedAt(Instant.now());
        appUserRepository.save(user);

        return new FcmTokenResponse(true, user.getId(), deviceId, user.getFcmTokenUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<FcmTokenDetailsResponse> getFcmTokens(Long userId) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));

        return userDeviceRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(userDevice -> new FcmTokenDetailsResponse(
                true,
                user.getId(),
                userDevice.getDeviceId(),
                userDevice.getFcmToken(),
                user.getFcmTokenUpdatedAt()
            ))
            .toList();
    }

    @Transactional
    public LogoutResponse logout(LogoutRequest request, LoginAuditContext auditContext) {
        String identifier = resolveLogoutIdentifier(request);
        AppUser user = resolveLogoutUser(request, identifier);

        Instant loggedAt = Instant.now();
        logAuthEvent("LOGOUT", user, request.grantType(), request.scope(), request.osType(), request.apiVersion(), request.deviceId(), identifier, auditContext, loggedAt);
        clearUserDevice(user, request.deviceId());

        return new LogoutResponse(
            true,
            "Logged out successfully.",
            new LoginContextResponse(
                identifier,
                trimOrNull(request.grantType()),
                trimOrNull(request.scope()),
                trimOrNull(request.osType()),
                trimOrNull(request.apiVersion()),
                trimOrNull(request.deviceId()),
                trimOrNull(auditContext.ipAddress()),
                trimOrNull(auditContext.userAgent()),
                loggedAt
            )
        );
    }

    @Transactional(readOnly = true)
    public List<LoginLogResponse> listLoginLogs(Long userId) {
        List<LoginLog> logs = userId == null
            ? loginLogRepository.findTop200ByOrderByCreatedAtDesc()
            : loginLogRepository.findTop200ByUserIdOrderByCreatedAtDesc(userId);

        return logs.stream()
            .map(log -> new LoginLogResponse(
                log.getId(),
                log.getEventType(),
                log.getUser() != null ? log.getUser().getId() : null,
                log.getLoginIdentifier(),
                log.getGrantType(),
                log.getScope(),
                log.getOsType(),
                log.getApiVersion(),
                log.getDeviceId(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt()
            ))
            .toList();
    }

    public static UserResponse toUserResponse(AppUser user) {
        List<Long> managedLocationIds = user.getManagedLocations().stream().map(Location::getId).sorted().toList();
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getContactNumber(),
            user.getAddress(),
            user.getRole().getCode(),
            user.getCompany() != null ? user.getCompany().getId() : null,
            user.getLocation() != null ? user.getLocation().getId() : null,
            user.isAllCompanyLocations(),
            managedLocationIds
        );
    }

    private UserRole mapRole(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("userRole is required.");
        }

        return switch (code) {
            case 1 -> UserRole.SUPER_ADMIN;
            case 2 -> UserRole.COMPANY_ADMIN;
            case 3 -> UserRole.PORTAL_USER;
            case 4 -> UserRole.MOBILE_APP_USER;
            default -> throw new IllegalArgumentException("Invalid userRole. Use 1=super admin, 2=company admin, 3=portal user, 4=mobile app user.");
        };
    }

    private void assignCompany(AppUser user, Long companyId) {
        if (companyId == null) {
            return;
        }
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found."));
        user.setCompany(company);
    }

    private void assignLocation(AppUser user, Long locationId) {
        if (locationId == null) {
            return;
        }
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new IllegalArgumentException("Location not found."));
        user.setLocation(location);
    }

    private void syncManagedLocations(AppUser user, List<Long> managedLocationIds) {
        if (managedLocationIds == null) {
            return;
        }
        Set<Location> locations = new HashSet<>(locationRepository.findAllById(managedLocationIds));
        if (locations.size() != managedLocationIds.size()) {
            throw new IllegalArgumentException("One or more managedLocationIds are invalid.");
        }
        user.setManagedLocations(locations);
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

    private String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String resolveLoginIdentifier(LoginRequest request) {
        String identifier = trimOrNull(request.email());
        if (identifier == null) {
            identifier = trimOrNull(request.username());
        }
        if (identifier == null) {
            throw new IllegalArgumentException("email (or username) is required.");
        }
        return identifier.toLowerCase();
    }

    private void logAuthEvent(
        String eventType,
        AppUser user,
        String grantType,
        String scope,
        String osType,
        String apiVersion,
        String deviceId,
        String identifier,
        LoginAuditContext auditContext,
        Instant loggedAt
    ) {
        LoginLog log = new LoginLog();
        log.setUser(user);
        log.setEventType(eventType);
        log.setLoginIdentifier(identifier);
        log.setGrantType(trimOrNull(grantType));
        log.setScope(trimOrNull(scope));
        log.setOsType(trimOrNull(osType));
        log.setApiVersion(trimOrNull(apiVersion));
        log.setDeviceId(trimOrNull(deviceId));
        log.setIpAddress(trimOrNull(auditContext.ipAddress()));
        log.setUserAgent(trimOrNull(auditContext.userAgent()));
        log.setCreatedAt(loggedAt);
        loginLogRepository.save(log);
    }

    private AppUser resolveLogoutUser(LogoutRequest request, String identifier) {
        if (request.userId() != null) {
            return appUserRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        }
        return appUserRepository.findByEmailIgnoreCase(identifier)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    private String resolveLogoutIdentifier(LogoutRequest request) {
        String identifier = trimOrNull(request.email());
        if (identifier == null) {
            identifier = trimOrNull(request.username());
        }
        if (identifier == null && request.userId() != null) {
            AppUser user = appUserRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
            identifier = user.getEmail();
        }
        if (identifier == null) {
            throw new IllegalArgumentException("userId or email (or username) is required.");
        }
        return identifier.toLowerCase();
    }

    private void upsertUserDevice(AppUser user, LoginRequest request, Instant loggedAt) {
        String deviceId = trimOrNull(request.deviceId());
        if (deviceId == null) {
            return;
        }
        UserDevice userDevice = userDeviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId)
            .orElseGet(UserDevice::new);
        if (userDevice.getId() == null) {
            userDevice.setUser(user);
            userDevice.setDeviceId(deviceId);
            userDevice.setCreatedAt(loggedAt);
        }
        userDevice.setOsType(trimOrNull(request.osType()));
        userDevice.setOsVersion(trimOrNull(request.osVersion()));
        userDevice.setApiVersion(trimOrNull(request.apiVersion()));
        userDevice.setLastLogin(loggedAt);
        userDeviceRepository.save(userDevice);
    }

    private void clearUserDevice(AppUser user, String requestDeviceId) {
        String deviceId = trimOrNull(requestDeviceId);
        if (deviceId == null) {
            return;
        }
        userDeviceRepository.deleteByUserIdAndDeviceId(user.getId(), deviceId);
    }
}
