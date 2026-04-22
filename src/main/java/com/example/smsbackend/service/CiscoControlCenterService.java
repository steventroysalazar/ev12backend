package com.example.smsbackend.service;

import com.example.smsbackend.config.CiscoControlCenterProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class CiscoControlCenterService {

    private final CiscoControlCenterProperties properties;
    private final RestTemplate restTemplate;

    public CiscoControlCenterService(CiscoControlCenterProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.timeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public CiscoDeviceDetails fetchDeviceDetails(String iccid) {
        validateConfiguration();
        String url = buildUrl(properties.deviceDetailsPath(), iccid);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new GatewayClientException(502, "Cisco Control Center returned an empty body for device details.");
            }
            return new CiscoDeviceDetails(
                asString(body.get("iccid")),
                asString(body.get("msisdn")),
                asString(body.get("status"))
            );
        } catch (HttpStatusCodeException exception) {
            throw new GatewayClientException(
                exception.getStatusCode().value(),
                "Cisco Control Center device details failed: " + exception.getResponseBodyAsString(),
                exception
            );
        } catch (GatewayClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GatewayClientException(502, "Cisco Control Center device details request failed.", exception);
        }
    }

    public void activateSim(String iccid) {
        postAction(properties.activatePath(), iccid, "activate");
    }

    public void deactivateSim(String iccid) {
        postAction(properties.deactivatePath(), iccid, "deactivate");
    }

    private void postAction(String pathTemplate, String iccid, String action) {
        validateConfiguration();
        String url = buildUrl(pathTemplate, iccid);
        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers()), Void.class);
        } catch (HttpStatusCodeException exception) {
            throw new GatewayClientException(
                exception.getStatusCode().value(),
                "Cisco Control Center SIM " + action + " failed: " + exception.getResponseBodyAsString(),
                exception
            );
        } catch (Exception exception) {
            throw new GatewayClientException(502, "Cisco Control Center SIM " + action + " request failed.", exception);
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, basicAuth());
        return headers;
    }

    private String basicAuth() {
        String token = properties.username() + ":" + properties.apiKey();
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private String buildUrl(String pathTemplate, String iccid) {
        if (!StringUtils.hasText(iccid)) {
            throw new IllegalArgumentException("ICCID is required to call Cisco Control Center.");
        }
        String host = properties.host();
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "https://" + host;
        }
        String normalizedHost = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        String path = pathTemplate.replace("{iccid}", iccid.trim());
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return normalizedHost + path;
    }

    private void validateConfiguration() {
        if (!properties.enabled()) {
            throw new IllegalArgumentException("Cisco Control Center integration is disabled.");
        }
        if (!StringUtils.hasText(properties.host())) {
            throw new IllegalArgumentException("Cisco Control Center host is not configured.");
        }
        if (!StringUtils.hasText(properties.username()) || !StringUtils.hasText(properties.apiKey())) {
            throw new IllegalArgumentException("Cisco Control Center credentials are not configured.");
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    public record CiscoDeviceDetails(String iccid, String msisdn, String status) {
        public boolean activated() {
            return "ACTIVATED".equalsIgnoreCase(status);
        }
    }
}
