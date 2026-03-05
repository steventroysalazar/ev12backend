package com.example.smsbackend.repository;

import com.example.smsbackend.entity.Ev12WebhookEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Ev12WebhookEventRepository extends JpaRepository<Ev12WebhookEvent, Long> {

    List<Ev12WebhookEvent> findTop100ByOrderByReceivedAtDesc();
}
