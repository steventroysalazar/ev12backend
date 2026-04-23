package com.example.smsbackend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.smsbackend.entity.AppUser;
import com.example.smsbackend.entity.Company;
import com.example.smsbackend.entity.Device;
import com.example.smsbackend.entity.UserRole;
import com.example.smsbackend.repository.AppUserRepository;
import com.example.smsbackend.repository.CompanyRepository;
import com.example.smsbackend.repository.DeviceRepository;
import com.example.smsbackend.service.CiscoControlCenterService;
import com.example.smsbackend.service.DeviceImeiService;
import com.example.smsbackend.service.GatewayClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:device-settings-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "gateway.base-url=http://localhost"
})
class DeviceProtocolSettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @MockBean
    private GatewayClientService gatewayClientService;

    @MockBean
    private DeviceImeiService deviceImeiService;

    @MockBean
    private CiscoControlCenterService ciscoControlCenterService;

    @Test
    void sendConfigAndGetDevice_roundTripsCanonicalProtocolSettings() throws Exception {
        Device device = seedDevice();

        String payload = """
            {
              "deviceId": %d,
              "whitelisted_numbers": ["+15550000001", "+15550000002", "+15550000003", "+15550000004"],
              "wifi_enabled": 1,
              "geo_fences": [
                {"slot": 1, "enabled": "1", "mode": "0", "radius": "150"},
                {"slot": 2, "enabled": "0", "mode": "1", "radius": "200"},
                {"slot": 3, "enabled": "1", "mode": "1", "radius": "250"}
              ]
            }
            """.formatted(device.getId());

        mockMvc.perform(post("/api/send-config")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk());

        String responseBody = mockMvc.perform(get("/api/devices/{deviceId}", device.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode protocolSettings = objectMapper.readTree(responseBody).path("protocolSettings");

        JsonNode authorizedNumbers = protocolSettings.path("authorizedNumbers");
        Assertions.assertEquals(4, authorizedNumbers.size());
        Assertions.assertEquals("+15550000001", authorizedNumbers.get(0).asText());
        Assertions.assertEquals("+15550000002", authorizedNumbers.get(1).asText());
        Assertions.assertEquals("+15550000003", authorizedNumbers.get(2).asText());
        Assertions.assertEquals("+15550000004", authorizedNumbers.get(3).asText());

        Assertions.assertEquals("1", protocolSettings.path("wifiEnabled").asText());

        JsonNode geoFences = protocolSettings.path("geoFences");
        Assertions.assertEquals(3, geoFences.size());
        Assertions.assertEquals(1, geoFences.get(0).path("slot").asInt());
        Assertions.assertEquals("1", geoFences.get(0).path("enabled").asText());
        Assertions.assertEquals("0", geoFences.get(0).path("mode").asText());
        Assertions.assertEquals("150", geoFences.get(0).path("radius").asText());
        Assertions.assertEquals(2, geoFences.get(1).path("slot").asInt());
        Assertions.assertEquals("0", geoFences.get(1).path("enabled").asText());
        Assertions.assertEquals("1", geoFences.get(1).path("mode").asText());
        Assertions.assertEquals("200", geoFences.get(1).path("radius").asText());
        Assertions.assertEquals(3, geoFences.get(2).path("slot").asInt());
        Assertions.assertEquals("1", geoFences.get(2).path("enabled").asText());
        Assertions.assertEquals("1", geoFences.get(2).path("mode").asText());
        Assertions.assertEquals("250", geoFences.get(2).path("radius").asText());
    }

    private Device seedDevice() {
        Company company = new Company();
        company.setName("Acme");
        company.setAlarmReceiverIncluded(false);
        company.setAlarmReceiverEnabled(false);
        company = companyRepository.save(company);

        AppUser user = new AppUser();
        user.setEmail("it-device@example.com");
        user.setPasswordHash("hash");
        user.setFirstName("IT");
        user.setLastName("User");
        user.setRole(UserRole.PORTAL_USER);
        user.setCompany(company);
        user = appUserRepository.save(user);

        Device device = new Device();
        device.setUser(user);
        device.setName("Tracker");
        device.setPhoneNumber("+15551112222");
        device.setProtocolConfig("{}");
        return deviceRepository.save(device);
    }
}
