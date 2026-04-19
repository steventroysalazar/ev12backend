package com.example.smsbackend.service;

import com.example.smsbackend.dto.AuthResponse;
import com.example.smsbackend.dto.CreateUserRequest;
import com.example.smsbackend.dto.LoginRequest;
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
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
        AppUserRepository appUserRepository,
        CompanyRepository companyRepository,
        LocationRepository locationRepository,
        DeviceRepository deviceRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.deviceRepository = deviceRepository;
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

        if (request.managerId() != null) {
            AppUser manager = appUserRepository.findById(request.managerId())
                .orElseThrow(() -> new IllegalArgumentException("Manager not found."));
            if (manager.getRole() != UserRole.COMPANY_ADMIN) {
                throw new IllegalArgumentException("Assigned manager must have role 2 (COMPANY_ADMIN).");
            }
            user.setManager(manager);
        }

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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email().trim())
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        String tokenRaw = user.getEmail() + ":" + Instant.now().toEpochMilli();
        String token = Base64.getEncoder().encodeToString(tokenRaw.getBytes(StandardCharsets.UTF_8));
        return new AuthResponse(true, token, toUserResponse(user));
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
            user.getManager() != null ? user.getManager().getId() : null,
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

        if ((user.getRole() == UserRole.PORTAL_USER || user.getRole() == UserRole.MOBILE_APP_USER) && user.getManager() == null) {
            throw new IllegalArgumentException("Roles 3 and 4 users must be assigned to a company admin (role 2).");
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
}
