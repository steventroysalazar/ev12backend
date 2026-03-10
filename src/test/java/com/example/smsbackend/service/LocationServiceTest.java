package com.example.smsbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.smsbackend.dto.UpdateLocationRequest;
import com.example.smsbackend.entity.Location;
import com.example.smsbackend.repository.AppUserRepository;
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
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private DeviceRepository deviceRepository;

    private LocationService service;

    @BeforeEach
    void setUp() {
        service = new LocationService(locationRepository, appUserRepository, deviceRepository);
    }

    @Test
    void updateLocation_updatesNameAndClearsDetails() {
        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 11L);
        location.setName("Old");
        location.setDetails("Old details");

        when(locationRepository.findById(11L)).thenReturn(Optional.of(location));
        when(locationRepository.findByNameIgnoreCase("New HQ")).thenReturn(Optional.empty());
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findByLocationId(11L)).thenReturn(java.util.List.of());
        when(deviceRepository.countByUserLocationId(11L)).thenReturn(0L);

        var response = service.updateLocation(11L, new UpdateLocationRequest("New HQ", ""));

        assertEquals("New HQ", response.name());
        assertEquals(null, response.details());
    }
}
