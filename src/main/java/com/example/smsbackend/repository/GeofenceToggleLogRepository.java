package com.example.smsbackend.repository;

import com.example.smsbackend.entity.GeofenceToggleLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeofenceToggleLogRepository extends JpaRepository<GeofenceToggleLog, Long> {
    List<GeofenceToggleLog> findTop200ByOrderByCreatedAtDesc();
}
