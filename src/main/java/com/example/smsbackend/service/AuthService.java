package com.example.smsbackend.service;

import com.example.smsbackend.dto.AuthResponse;
import com.example.smsbackend.dto.CreateUserRequest;
import com.example.smsbackend.dto.LoginRequest;
import com.example.smsbackend.dto.UserResponse;
import com.example.smsbackend.entity.AppUser;
import com.example.smsbackend.entity.Location;
import com.example.smsbackend.entity.UserRole;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.LocationRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AppUserRepository appUserRepository, LocationRepository locationRepository) {
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
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

        if (request.locationId() != null) {
            Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new IllegalArgumentException("Location not found."));
            user.setLocation(location);
        }

        if (request.managerId() != null) {
            AppUser manager = appUserRepository.findById(request.managerId())
                .orElseThrow(() -> new IllegalArgumentException("Manager not found."));
            if (manager.getRole() != UserRole.MANAGER) {
                throw new IllegalArgumentException("Assigned manager must have role 2 (MANAGER).");
            }
            user.setManager(manager);
        }

        if (role == UserRole.USER && user.getManager() == null) {
            throw new IllegalArgumentException("Role 3 user must be assigned to a manager (role 2).");
        }

        AppUser saved = appUserRepository.save(user);
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
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getContactNumber(),
            user.getAddress(),
            user.getRole().getCode(),
            user.getLocation() != null ? user.getLocation().getId() : null,
            user.getManager() != null ? user.getManager().getId() : null
        );
    }

    private UserRole mapRole(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("userRole is required.");
        }

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
}
