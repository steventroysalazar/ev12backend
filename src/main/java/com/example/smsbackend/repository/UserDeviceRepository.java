package com.example.smsbackend.repository;

import com.example.smsbackend.entity.UserDevice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUserIdAndDeviceId(Long userId, String deviceId);
    void deleteByUserIdAndDeviceId(Long userId, String deviceId);
}
