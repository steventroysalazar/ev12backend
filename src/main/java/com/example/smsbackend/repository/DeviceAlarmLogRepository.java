package com.example.smsbackend.repository;

import com.example.smsbackend.entity.DeviceAlarmLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceAlarmLogRepository extends JpaRepository<DeviceAlarmLog, Long> {
    List<DeviceAlarmLog> findByDeviceIdOrderByEventAtDesc(Long deviceId);
}
