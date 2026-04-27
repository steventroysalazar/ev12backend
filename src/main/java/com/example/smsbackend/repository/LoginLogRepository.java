package com.example.smsbackend.repository;

import com.example.smsbackend.entity.LoginLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {
    List<LoginLog> findTop200ByOrderByCreatedAtDesc();
    List<LoginLog> findTop200ByUserIdOrderByCreatedAtDesc(Long userId);
}
