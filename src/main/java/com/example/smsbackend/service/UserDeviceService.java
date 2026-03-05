package com.example.smsbackend.service;

import com.example.smsbackend.dto.CreateDeviceRequest;
import com.example.smsbackend.dto.DeviceResponse;
import com.example.smsbackend.dto.UserResponse;
import com.example.smsbackend.entity.AppUser;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.entity.UserRole;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.DeviceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDeviceService {

    private final AppUserRepository appUserRepository;
    private final DeviceRepository deviceRepository;

    public UserDeviceService(AppUserRepository appUserRepository, DeviceRepository deviceRepository) {
        this.appUserRepository = appUserRepository;
        this.deviceRepository = deviceRepository;
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
    public DeviceResponse createDevice(Long userId, CreateDeviceRequest request) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));

        Device device = new Device();
        device.setUser(user);
        device.setName(request.name().trim());
        device.setPhoneNumber(request.phoneNumber().trim());
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
    public Device getDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found."));
    }

    private DeviceResponse toDeviceResponse(Device device) {
        return new DeviceResponse(
            device.getId(),
            device.getUser().getId(),
            device.getName(),
            device.getPhoneNumber()
        );
    }
}
