package com.example.smsbackend.service;

import com.example.smsbackend.dto.CompanyResponse;
import com.example.smsbackend.dto.CreateCompanyRequest;
import com.example.smsbackend.dto.UpdateCompanyRequest;
import com.example.smsbackend.entity.Company;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.CompanyRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final AppUserRepository appUserRepository;
    private final DeviceRepository deviceRepository;

    public CompanyService(
        CompanyRepository companyRepository,
        LocationRepository locationRepository,
        AppUserRepository appUserRepository,
        DeviceRepository deviceRepository
    ) {
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.appUserRepository = appUserRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        companyRepository.findByNameIgnoreCase(request.name().trim()).ifPresent(existing -> {
            throw new IllegalArgumentException("Company already exists.");
        });

        Company company = new Company();
        company.setName(request.name().trim());
        company.setDetails(StringUtils.hasText(request.details()) ? request.details().trim() : null);

        Company saved = companyRepository.save(company);
        return toResponse(saved);
    }

    @Transactional
    public CompanyResponse updateCompany(Long companyId, UpdateCompanyRequest request) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found."));

        if (StringUtils.hasText(request.name())) {
            String normalizedName = request.name().trim();
            companyRepository.findByNameIgnoreCase(normalizedName).ifPresent(existing -> {
                if (!existing.getId().equals(company.getId())) {
                    throw new IllegalArgumentException("Company already exists.");
                }
            });
            company.setName(normalizedName);
        }

        if (request.details() != null) {
            company.setDetails(StringUtils.hasText(request.details()) ? request.details().trim() : null);
        }

        return toResponse(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> listCompanies() {
        return companyRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    private CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
            company.getId(),
            company.getName(),
            company.getDetails(),
            locationRepository.countByCompanyId(company.getId()),
            appUserRepository.countByCompanyId(company.getId()),
            deviceRepository.countByUserCompanyId(company.getId())
        );
    }
}
