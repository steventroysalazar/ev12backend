package com.example.smsbackend.repository;

import com.example.smsbackend.entity.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    List<AppUser> findByLocationId(Long locationId);

    List<AppUser> findByManagerId(Long managerId);
}
