package com.example.smsbackend.repository;

import com.example.smsbackend.entity.ErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {
    Page<ErrorLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
