package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smsbackend.dto.CreateUserDeviceRequest;
import com.example.smsbackend.dto.CreateUserRequest;
import com.example.smsbackend.entity.AppUser;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.CompanyRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private DeviceRepository deviceRepository;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(appUserRepository, companyRepository, locationRepository, deviceRepository);
    }

    @Test
    void register_createsDeviceWhenProvided() {
        when(appUserRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.empty());
        when(deviceRepository.findByExternalDeviceId("862667084205114")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 20L);
            return saved;
        });

        var response = service.register(new CreateUserRequest(
            "John",
            "Doe",
            "john@example.com",
            "StrongPassword123",
            "+15550123",
            "123 Main St",
            2,
            null,
            null,
            null,
            null,
            null,
            new CreateUserDeviceRequest("Truck GPS 01", "+1555999000", "862667084205114")
        ));

        assertEquals(10L, response.id());
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    void register_rejectsDuplicateDeviceId() {
        when(appUserRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deviceRepository.findByExternalDeviceId("862667084205114")).thenReturn(Optional.of(existingDevice()));

        assertThrows(IllegalArgumentException.class, () -> service.register(new CreateUserRequest(
            "John",
            "Doe",
            "john@example.com",
            "StrongPassword123",
            null,
            null,
            2,
            null,
            null,
            null,
            null,
            null,
            new CreateUserDeviceRequest("Truck GPS 01", "+1555999000", "862667084205114")
        )));

        verify(deviceRepository, never()).save(any(Device.class));
    }

    private Device existingDevice() {
        Device device = new Device();
        device.setName("Existing");
        device.setPhoneNumber("+100");
        return device;
    }
}
