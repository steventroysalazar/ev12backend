package com.example.smsbackend.service;

import com.example.smsbackend.dto.CompanyResponse;
import com.example.smsbackend.dto.CreateCompanyRequest;
import com.example.smsbackend.dto.UpdateCompanyAlarmReceiverRequest;
import com.example.smsbackend.dto.UpdateCompanyRequest;
import com.example.smsbackend.entity.Company;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.CompanyRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.repository.LocationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompanyService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final AppUserRepository appUserRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    public CompanyService(
        CompanyRepository companyRepository,
        LocationRepository locationRepository,
        AppUserRepository appUserRepository,
        DeviceRepository deviceRepository,
        ObjectMapper objectMapper
    ) {
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.appUserRepository = appUserRepository;
        this.deviceRepository = deviceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        String normalizedName = normalizeRequiredName(request.name(), request.companyName());
        companyRepository.findByNameIgnoreCase(normalizedName).ifPresent(existing -> {
            throw new IllegalArgumentException("Company already exists.");
        });

        Company company = new Company();
        company.setName(normalizedName);
        applyCoreCompanyFields(company, request.details(), request.address(), request.city(), request.state(), request.postalCode(),
            request.country(), request.phone(), request.isAlarmReceiverIncluded());

        Company saved = companyRepository.save(company);
        return toResponse(saved);
    }

    @Transactional
    public CompanyResponse updateCompany(Long companyId, UpdateCompanyRequest request) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found."));

        String nextName = normalizeOptionalName(request.name(), request.companyName());
        if (nextName != null) {
            companyRepository.findByNameIgnoreCase(nextName).ifPresent(existing -> {
                if (!existing.getId().equals(company.getId())) {
                    throw new IllegalArgumentException("Company already exists.");
                }
            });
            company.setName(nextName);
        }

        applyCoreCompanyFields(company, request.details(), request.address(), request.city(), request.state(), request.postalCode(),
            request.country(), request.phone(), request.isAlarmReceiverIncluded());

        return toResponse(companyRepository.save(company));
    }

    @Transactional
    public CompanyResponse updateAlarmReceiverConfig(Long companyId, UpdateCompanyAlarmReceiverRequest request) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found."));

        if (request.alarmReceiverEnabled() != null) {
            company.setAlarmReceiverEnabled(request.alarmReceiverEnabled());
        }

        JsonNode mergedConfig = parseJsonNode(company.getAlarmReceiverConfigJson());
        if (request.alarmReceiverConfig() != null) {
            mergedConfig = request.alarmReceiverConfig().deepCopy();
        }

        if (mergedConfig instanceof ObjectNode objectNode && request.alarmReceiverEnabled() != null) {
            objectNode.put("en", request.alarmReceiverEnabled());
        }

        company.setAlarmReceiverConfigJson(toJson(mergedConfig));

        if (request.dnsWhitelist() != null) {
            List<String> sanitizedDns = sanitizeList(request.dnsWhitelist());
            company.setWhitelistedDnsJson(toJson(sanitizedDns));
        }

        if (request.ipWhitelist() != null) {
            List<String> sanitizedIps = sanitizeList(request.ipWhitelist());
            company.setWhitelistedIpsJson(toJson(sanitizedIps));
        }

        return toResponse(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> listCompanies() {
        return companyRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    private String normalizeRequiredName(String name, String companyName) {
        String value = StringUtils.hasText(name) ? name.trim() : (StringUtils.hasText(companyName) ? companyName.trim() : null);
        if (value == null) {
            throw new IllegalArgumentException("Company name is required.");
        }
        return value;
    }

    private String normalizeOptionalName(String name, String companyName) {
        if (name != null) {
            return StringUtils.hasText(name) ? name.trim() : null;
        }
        if (companyName != null) {
            return StringUtils.hasText(companyName) ? companyName.trim() : null;
        }
        return null;
    }

    private void applyCoreCompanyFields(
        Company company,
        String details,
        String address,
        String city,
        String state,
        String postalCode,
        String country,
        String phone,
        Boolean isAlarmReceiverIncluded
    ) {
        if (details != null) {
            company.setDetails(trimOrNull(details));
        }
        if (address != null) {
            company.setAddress(trimOrNull(address));
        }
        if (city != null) {
            company.setCity(trimOrNull(city));
        }
        if (state != null) {
            company.setState(trimOrNull(state));
        }
        if (postalCode != null) {
            company.setPostalCode(trimOrNull(postalCode));
        }
        if (country != null) {
            company.setCountry(trimOrNull(country));
        }
        if (phone != null) {
            company.setPhone(trimOrNull(phone));
        }
        if (isAlarmReceiverIncluded != null) {
            company.setAlarmReceiverIncluded(isAlarmReceiverIncluded);
        }
    }

    private String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> sanitizeList(List<String> input) {
        return input.stream()
            .map(this::trimOrNull)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize company configuration.", exception);
        }
    }

    private JsonNode parseJsonNode(String json) {
        if (!StringUtils.hasText(json) || "null".equals(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to parse alarm receiver config.", exception);
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json) || "null".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to parse whitelist values.", exception);
        }
    }

    private CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
            company.getId(),
            company.getName(),
            company.getName(),
            company.getDetails(),
            company.getAddress(),
            company.getCity(),
            company.getState(),
            company.getPostalCode(),
            company.getCountry(),
            company.getPhone(),
            company.isAlarmReceiverIncluded(),
            company.isAlarmReceiverEnabled(),
            parseJsonNode(company.getAlarmReceiverConfigJson()),
            parseStringList(company.getWhitelistedDnsJson()),
            parseStringList(company.getWhitelistedIpsJson()),
            locationRepository.countByCompanyId(company.getId()),
            appUserRepository.countByCompanyId(company.getId()),
            deviceRepository.countByUserCompanyId(company.getId())
        );
    }
}
