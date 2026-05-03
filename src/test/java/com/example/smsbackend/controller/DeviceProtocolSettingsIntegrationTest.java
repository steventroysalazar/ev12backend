package com.example.smsbackend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    "spring.jpa.hibernate.ddl-auto=create-drop"
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
    private DeviceImeiService deviceImeiService;

    @MockBean
    private CiscoControlCenterService ciscoControlCenterService;

    @Test
    void putDeviceWithSnakeProtocolSettings_roundTripsFullCanonicalConfig() throws Exception {
        Device device = seedDevice();

        String payload = """
            {
              "name": "Tracker X",
              "protocol_settings": {
                "imei": "860000000000001",
                "eview_version": "1.2.3",
                "contacts": [
                  {"slot": 1, "sms_enabled": true, "call_enabled": true, "phone": "+15550000001", "name": "Ops1"},
                  {"slot": 2, "sms_enabled": false, "call_enabled": true, "phone": "+15550000002", "name": "Ops2"}
                ],
                "sms_password": "123456",
                "sms_whitelist_enabled": true,
                "wifi_enabled": 1,
                "speaker_volume": 77,
                "sos_mode": 2,
                "sos_action_time": 45,
                "sos_call_ring_time": "40S",
                "sos_call_talk_time": "50S",
                "fall_down_enabled": true,
                "fall_down_sensitivity": 4,
                "fall_down_call": true,
                "no_motion_enabled": true,
                "no_motion_time": "20M",
                "no_motion_call": true,
                "motion_enabled": true,
                "motion_static_time": "10M",
                "motion_duration_time": "15S",
                "motion_call": true,
                "over_speed_enabled": true,
                "over_speed_limit": "80",
                "continuous_locate_interval": "15S",
                "continuous_locate_duration": "900S",
                "time_zone": "+8",
                "check_status": true,
                "authorized_numbers": ["+15551111111", "+15552222222", "+15553333333", "+15554444444"],
                "geo_fences": [
                  {"slot": 1, "enabled": "1", "mode": "0", "radius": "150", "latitude": 14.5995, "longitude": 120.9842},
                  {"slot": 2, "enabled": "0", "mode": "1", "radius": "300", "latitude": 14.5547, "longitude": 121.0244},
                  {"slot": 3, "enabled": "1", "mode": "1", "radius": "500", "latitude": 14.6760, "longitude": 121.0437}
                ]
              }
            }
            """;

        mockMvc.perform(put("/api/devices/{deviceId}", device.getId())
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk());

        String responseBody = mockMvc.perform(get("/api/devices/{deviceId}", device.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode protocolSettings = objectMapper.readTree(responseBody).path("protocolSettings");

        JsonNode expected = objectMapper.readTree("""
            {
              "imei": "860000000000001",
              "eviewVersion": "1.2.3",
              "contacts": [
                {"slot": 1, "smsEnabled": true, "callEnabled": true, "phone": "+15550000001", "name": "Ops1"},
                {"slot": 2, "smsEnabled": false, "callEnabled": true, "phone": "+15550000002", "name": "Ops2"}
              ],
              "smsPassword": "123456",
              "smsWhitelistEnabled": true,
              "wifiEnabled": "1",
              "speakerVolume": 77,
              "sosMode": 2,
              "sosActionTime": 45,
              "sosCallRingTime": "40S",
              "sosCallTalkTime": "50S",
              "fallDownEnabled": true,
              "fallDownSensitivity": 4,
              "fallDownCall": true,
              "noMotionEnabled": true,
              "noMotionTime": "20M",
              "noMotionCall": true,
              "motionEnabled": true,
              "motionStaticTime": "10M",
              "motionDurationTime": "15S",
              "motionCall": true,
              "overSpeedEnabled": true,
              "overSpeedLimit": "80",
              "continuousLocateInterval": "15S",
              "continuousLocateDuration": "900S",
              "timeZone": "+8",
              "checkStatus": true,
              "authorizedNumbers": ["+15551111111", "+15552222222", "+15553333333", "+15554444444"],
              "geoFences": [
                {"slot": 1, "enabled": "1", "mode": "0", "radius": "150", "latitude": 14.5995, "longitude": 120.9842},
                {"slot": 2, "enabled": "0", "mode": "1", "radius": "300", "latitude": 14.5547, "longitude": 121.0244},
                {"slot": 3, "enabled": "1", "mode": "1", "radius": "500", "latitude": 14.6760, "longitude": 121.0437}
              ]
            }
            """);

        Assertions.assertEquals(expected, protocolSettings);
    }

    @Test
    void putDevice_mergeUpdateKeepsAuthorizedNumbersAndGeoFencesArrays() throws Exception {
        Device device = seedDevice();

        String initialPayload = """
            {
              "protocolSettings": {
                "authorizedNumbers": ["+10001", "+10002"],
                "geoFences": [
                  {"slot": 1, "enabled": "1", "mode": "0", "radius": "100"},
                  {"slot": 2, "enabled": "0", "mode": "1", "radius": "200"}
                ],
                "wifiEnabled": "1"
              }
            }
            """;

        mockMvc.perform(put("/api/devices/{deviceId}", device.getId())
                .contentType("application/json")
                .content(initialPayload))
            .andExpect(status().isOk());

        String mergePayload = """
            {
              "protocol_settings": {
                "wifi_positioning": false
              }
            }
            """;

        mockMvc.perform(put("/api/devices/{deviceId}", device.getId())
                .contentType("application/json")
                .content(mergePayload))
            .andExpect(status().isOk());

        String responseBody = mockMvc.perform(get("/api/devices/{deviceId}", device.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode protocolSettings = objectMapper.readTree(responseBody).path("protocolSettings");
        Assertions.assertEquals("0", protocolSettings.path("wifiEnabled").asText());
        Assertions.assertEquals(objectMapper.readTree("[\"+10001\",\"+10002\"]"), protocolSettings.path("authorizedNumbers"));
        Assertions.assertEquals(objectMapper.readTree("""
            [
              {"slot":1,"enabled":"1","mode":"0","radius":"100"},
              {"slot":2,"enabled":"0","mode":"1","radius":"200"}
            ]
            """), protocolSettings.path("geoFences"));
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
