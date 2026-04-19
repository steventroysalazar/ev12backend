package com.example.smsbackend.controller;

import com.example.smsbackend.dto.CompanyResponse;
import com.example.smsbackend.dto.CreateCompanyRequest;
import com.example.smsbackend.dto.UpdateCompanyAlarmReceiverRequest;
import com.example.smsbackend.dto.UpdateCompanyRequest;
import com.example.smsbackend.service.CompanyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/companies", "api/companies"})
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.ok(companyService.createCompany(request));
    }

    @PutMapping("/{companyId}")
    public ResponseEntity<CompanyResponse> updateCompany(
        @PathVariable Long companyId,
        @Valid @RequestBody UpdateCompanyRequest request
    ) {
        return ResponseEntity.ok(companyService.updateCompany(companyId, request));
    }

    @PutMapping("/{companyId}/alarm-receiver")
    public ResponseEntity<CompanyResponse> updateAlarmReceiverConfig(
        @PathVariable Long companyId,
        @RequestBody UpdateCompanyAlarmReceiverRequest request
    ) {
        return ResponseEntity.ok(companyService.updateAlarmReceiverConfig(companyId, request));
    }

    @GetMapping
    public ResponseEntity<List<CompanyResponse>> listCompanies() {
        return ResponseEntity.ok(companyService.listCompanies());
    }
}
