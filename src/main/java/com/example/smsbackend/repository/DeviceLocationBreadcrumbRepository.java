package com.example.smsbackend.repository;

import com.example.smsbackend.entity.DeviceLocationBreadcrumb;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceLocationBreadcrumbRepository extends JpaRepository<DeviceLocationBreadcrumb, Long> {
    List<DeviceLocationBreadcrumb> findByDeviceIdOrderByCapturedAtDesc(Long deviceId);
}
