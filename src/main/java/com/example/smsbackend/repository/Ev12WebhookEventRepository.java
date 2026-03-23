package com.example.smsbackend.repository;

import com.example.smsbackend.entity.Ev12WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface Ev12WebhookEventRepository extends JpaRepository<Ev12WebhookEvent, Long> {

    Page<Ev12WebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
