package com.example.smsbackend.repository;

import com.example.smsbackend.entity.Device;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findByUserIdOrderByNameAsc(Long userId);

    long countByUserLocationId(Long locationId);

    List<Device> findByUserLocationId(Long locationId);
}
