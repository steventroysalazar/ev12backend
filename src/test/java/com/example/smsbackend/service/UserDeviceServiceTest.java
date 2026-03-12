package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.smsbackend.dto.DeviceContactSettings;
import com.example.smsbackend.dto.DeviceProtocolSettings;
import com.example.smsbackend.dto.UpdateDeviceRequest;
import com.example.smsbackend.dto.UpdateUserRequest;
import com.example.smsbackend.entity.AppUser;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.entity.Location;
import com.example.smsbackend.entity.UserRole;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserDeviceServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private LocationRepository locationRepository;

    private UserDeviceService service;

    @BeforeEach
    void setUp() {
        service = new UserDeviceService(appUserRepository, deviceRepository, locationRepository, new ObjectMapper());
    }

    @Test
    void updateUser_updatesManagerAndLocation() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setRole(UserRole.MANAGER);

        AppUser manager = new AppUser();
        ReflectionTestUtils.setField(manager, "id", 2L);
        manager.setRole(UserRole.MANAGER);

        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 3L);

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(locationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateUser(1L, new UpdateUserRequest(
            "New",
            "Name",
            null,
            null,
            null,
            null,
            3L,
            false,
            2L,
            false
        ));

        assertEquals(1L, response.id());
        assertEquals(3L, response.locationId());
        assertEquals(2L, response.managerId());
    }

    @Test
    void updateDevice_reassignsUser() {
        AppUser oldUser = new AppUser();
        ReflectionTestUtils.setField(oldUser, "id", 7L);

        AppUser newUser = new AppUser();
        ReflectionTestUtils.setField(newUser, "id", 8L);

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 9L);
        device.setUser(oldUser);
        device.setName("Original");
        device.setPhoneNumber("111");

        when(deviceRepository.findById(9L)).thenReturn(Optional.of(device));
        when(appUserRepository.findById(8L)).thenReturn(Optional.of(newUser));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateDevice(9L, new UpdateDeviceRequest("Renamed", "222", 8L, new DeviceProtocolSettings(
            java.util.List.of(new DeviceContactSettings(1, true, true, "123456789", "Emma")),
            "860000000000001", "1.0.5",
            "123456789", 1, true, true, "Emma", "123456", true, true, false, false, true, false,
            10, 90, true, true, true, "Emma", true, 1, 20, "35S", "20M", true, 5, true, true,
            "80M", true, false, null, null, null, true, "100km/h", true, 0, "100m", true,
            "internet", true, "www.smart-locator.com", 6060, true, "mode2", "03M", "01H",
            "10S", "600S", "+1", false, true, true, "10M", true, "10M", true
        )));

        assertEquals(9L, response.id());
        assertEquals(8L, response.userId());
        assertEquals("Renamed", response.name());
        assertEquals("222", response.phoneNumber());
        assertEquals("860000000000001", response.protocolSettings().imei());
        assertEquals("1.0.5", response.protocolSettings().eviewVersion());
        assertEquals("123456789", response.protocolSettings().contactNumber());
        assertEquals(1, response.protocolSettings().contacts().size());
    }

    @Test
    void updateUser_rejectsRole3WithoutManager() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setRole(UserRole.USER);

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> service.updateUser(1L, new UpdateUserRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true
        )));
    }

    @Test
    void markDeviceConfigPending_setsQueueState() {
        Device device = new Device();
        Instant sentAt = Instant.parse("2026-03-12T09:30:00Z");

        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markDeviceConfigPending(device, "loc,status", sentAt);

        assertEquals(UserDeviceService.CONFIG_STATUS_PENDING, device.getConfigStatus());
        assertEquals("loc,status", device.getConfigCommandPreview());
        assertEquals(sentAt, device.getConfigLastSentAt());
        assertEquals(null, device.getConfigAppliedAt());
    }

    @Test
    void canResend_requiresPendingAndCooldown() {
        Device device = new Device();
        device.setConfigStatus(UserDeviceService.CONFIG_STATUS_PENDING);
        device.setConfigLastSentAt(Instant.parse("2026-03-12T09:30:00Z"));

        assertFalse(service.canResend(device, Instant.parse("2026-03-12T09:34:59Z")));
        assertTrue(service.canResend(device, Instant.parse("2026-03-12T09:35:00Z")));

        device.setConfigStatus(UserDeviceService.CONFIG_STATUS_APPLIED);
        assertFalse(service.canResend(device, Instant.parse("2026-03-12T09:40:00Z")));
    }
}
