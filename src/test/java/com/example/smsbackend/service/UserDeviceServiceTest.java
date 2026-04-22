package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
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
import com.example.smsbackend.repository.CompanyRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
    private CompanyRepository companyRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private DeviceTelemetryLogService deviceTelemetryLogService;

    @Mock
    private DeviceImeiService deviceImeiService;

    @Mock
    private CiscoControlCenterService ciscoControlCenterService;

    private UserDeviceService service;

    @BeforeEach
    void setUp() {
        service = new UserDeviceService(appUserRepository, deviceRepository, locationRepository, companyRepository, new ObjectMapper(), deviceTelemetryLogService, deviceImeiService, ciscoControlCenterService);
    }

    @Test
    void updateUser_updatesLocation() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setRole(UserRole.SUPER_ADMIN);

        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 3L);

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(locationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateUser(1L, new UpdateUserRequest(
            "New",
            "Name",
            null,
            null,
            null,
            null,
            null,
            3L,
            false,
            null,
            null,
            null
        ));

        assertEquals(1L, response.id());
        assertEquals(3L, response.locationId());
    }

    @Test
    void getUserById_returnsUserDetails() {
        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 3L);
        location.setName("HQ");

        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmail("user@example.com");
        user.setFirstName("User");
        user.setLastName("Name");
        user.setContactNumber("+15551234567");
        user.setAddress("123 Main");
        user.setRole(UserRole.PORTAL_USER);
        user.setLocation(location);

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        var response = service.getUserById(1L);

        assertEquals(1L, response.id());
        assertEquals("user@example.com", response.email());
        assertEquals("User", response.firstName());
        assertEquals("Name", response.lastName());
        assertEquals(3L, response.locationId());
    }


    @Test
    void listCompanyAdminsLookup_returnsOnlyRole2Users() {
        AppUser manager = new AppUser();
        ReflectionTestUtils.setField(manager, "id", 2L);
        manager.setFirstName("Maya");
        manager.setLastName("Stone");
        manager.setEmail("maya@example.com");
        manager.setRole(UserRole.COMPANY_ADMIN);

        when(appUserRepository.findByRoleOrderByFirstNameAscLastNameAsc(UserRole.COMPANY_ADMIN))
            .thenReturn(List.of(manager));

        var response = service.listCompanyAdminsLookup();

        assertEquals(1, response.size());
        assertEquals(2L, response.get(0).id());
        assertEquals(2, response.get(0).userRole());
        verify(appUserRepository).findByRoleOrderByFirstNameAscLastNameAsc(UserRole.COMPANY_ADMIN);
    }

    @Test
    void listPortalUsersLookup_returnsOnlyRole3Users() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 3L);
        user.setFirstName("Uma");
        user.setLastName("Ray");
        user.setEmail("uma@example.com");
        user.setRole(UserRole.PORTAL_USER);

        when(appUserRepository.findByRoleOrderByFirstNameAscLastNameAsc(UserRole.PORTAL_USER))
            .thenReturn(List.of(user));

        var response = service.listPortalUsersLookup();

        assertEquals(1, response.size());
        assertEquals(3, response.get(0).userRole());
    }


    @Test
    void listUsersLookupByLocation_returnsLocationUsers() {
        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 11L);

        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 21L);
        user.setFirstName("Ari");
        user.setLastName("Lane");
        user.setEmail("ari@example.com");
        user.setRole(UserRole.PORTAL_USER);
        user.setLocation(location);

        when(locationRepository.existsById(11L)).thenReturn(true);
        when(appUserRepository.findByLocationIdOrderByFirstNameAscLastNameAsc(11L)).thenReturn(List.of(user));

        var response = service.listUsersLookupByLocation(11L);

        assertEquals(1, response.size());
        assertEquals(21L, response.get(0).id());
        assertEquals(3, response.get(0).userRole());
    }

    @Test
    void listLocationsLookup_returnsSortedLocationLookupItems() {
        Location hq = new Location();
        ReflectionTestUtils.setField(hq, "id", 10L);
        hq.setName("HQ");

        when(locationRepository.findAllByOrderByNameAsc()).thenReturn(List.of(hq));

        var response = service.listLocationsLookup();

        assertEquals(1, response.size());
        assertEquals(10L, response.get(0).id());
        assertEquals("HQ", response.get(0).name());
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

        var response = service.updateDevice(9L, new UpdateDeviceRequest("Renamed", "222", "862667084205114", null, 8L, new DeviceProtocolSettings(
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
        assertEquals("862667084205114", response.externalDeviceId());
        assertEquals("860000000000001", response.protocolSettings().imei());
        assertEquals("1.0.5", response.protocolSettings().eviewVersion());
        assertEquals("123456789", response.protocolSettings().contactNumber());
        assertEquals(1, response.protocolSettings().contacts().size());
    }

    @Test
    void updateDevice_updatesAssignedUsersLocation() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);

        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 3L);

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 9L);
        device.setUser(user);

        when(deviceRepository.findById(9L)).thenReturn(Optional.of(device));
        when(locationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setLocationId(3L);

        service.updateDevice(9L, request);

        assertEquals(3L, device.getUser().getLocation().getId());
    }

    @Test
    void updateDevice_rejectsLocationIdWhenClearLocationAlsoSet() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 9L);
        device.setUser(user);

        when(deviceRepository.findById(9L)).thenReturn(Optional.of(device));

        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setLocationId(3L);
        request.setClearLocation(true);

        assertThrows(IllegalArgumentException.class, () -> service.updateDevice(9L, request));
    }


    @Test
    void updateDevice_allowsAlarmCancelPayload() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 9L);
        device.setUser(user);
        device.setAlarmCode("SOS Alert");

        when(deviceRepository.findById(9L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setAlarmCode(null);
        Instant cancelledAt = Instant.parse("2026-03-22T22:00:00Z");
        request.setAlarmCancelledAt(cancelledAt);

        var response = service.updateDevice(9L, request);

        assertEquals(null, response.alarmCode());
        assertEquals(null, response.alarmTriggeredAt());
        assertEquals(cancelledAt, response.alarmCancelledAt());
    }

    @Test
    void updateDevice_setsAlarmTriggeredAtWhenAlarmCodeIsSet() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 9L);
        device.setUser(user);

        when(deviceRepository.findById(9L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setAlarmCode("Fall-Down Alert");

        var response = service.updateDevice(9L, request);

        assertEquals("Fall-Down Alert", response.alarmCode());
        assertNotNull(response.alarmTriggeredAt());
        assertEquals(null, response.alarmCancelledAt());
    }

    @Test
    void listAllDevices_ignoresInvalidProtocolSettingsJson() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 12L);

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 99L);
        device.setUser(user);
        device.setName("Tracker");
        device.setPhoneNumber("+111");
        device.setProtocolConfig("{not-valid-json}");

        when(deviceRepository.findAll()).thenReturn(java.util.List.of(device));

        var response = service.listAllDevices();

        assertEquals(1, response.size());
        assertEquals(99L, response.get(0).id());
        assertEquals(null, response.get(0).protocolSettings());
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

    @Test
    void saveDeviceProtocolSettings_mergesWithExistingConfig() {
        Device device = new Device();
        device.setProtocolConfig("""
            {"micVolume":55,"speakerVolume":65,"fallDownSensitivity":4}
            """);

        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveDeviceProtocolSettings(device, new DeviceProtocolSettings(
            null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null,
            70, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null
        ));

        assertTrue(device.getProtocolConfig().contains("\"micVolume\":70"));
        assertTrue(device.getProtocolConfig().contains("\"speakerVolume\":65"));
        assertTrue(device.getProtocolConfig().contains("\"fallDownSensitivity\":4"));
    }
}
