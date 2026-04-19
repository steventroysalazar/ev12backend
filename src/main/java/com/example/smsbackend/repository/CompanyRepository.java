package com.example.smsbackend.repository;

import com.example.smsbackend.entity.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByNameIgnoreCase(String name);

    List<Company> findAllByOrderByNameAsc();
}
