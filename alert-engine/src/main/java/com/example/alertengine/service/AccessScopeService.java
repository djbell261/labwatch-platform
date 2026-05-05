package com.example.alertengine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AccessScopeService {

    private static final Logger log = LoggerFactory.getLogger(AccessScopeService.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> MACHINE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final boolean authEnabled;
    private final RestClient monitoringApiClient;

    @Autowired
    public AccessScopeService(
            @Value("${labwatch.auth.enabled:false}") boolean authEnabled,
            @Value("${services.monitoring-api.base-url:http://monitoring-api:8089}") String monitoringApiBaseUrl,
            @Value("${services.http.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${services.http.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        this.authEnabled = authEnabled;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.monitoringApiClient = RestClient.builder()
                .baseUrl(monitoringApiBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    AccessScopeService(boolean authEnabled, RestClient monitoringApiClient) {
        this.authEnabled = authEnabled;
        this.monitoringApiClient = monitoringApiClient;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public Set<String> resolveAccessibleMachineIdentifiers(String authorizationHeader) {
        if (!authEnabled) {
            return Collections.emptySet();
        }

        String normalizedAuthorizationHeader = normalizeAuthorizationHeader(authorizationHeader);
        if (normalizedAuthorizationHeader == null) {
            log.warn("Authorization is enabled but no valid bearer token was provided for alert scope resolution.");
            return Collections.emptySet();
        }

        try {
            List<Map<String, Object>> response = monitoringApiClient.get()
                    .uri("/api/v1/machines")
                    .header(HttpHeaders.AUTHORIZATION, normalizedAuthorizationHeader)
                    .retrieve()
                    .body(MACHINE_LIST_TYPE);

            if (response == null) {
                return Collections.emptySet();
            }

            return response.stream()
                    .map(machine -> machine == null ? null : machine.get("machineIdentifier"))
                    .map(value -> value == null ? null : String.valueOf(value).trim())
                    .filter(identifier -> identifier != null && !identifier.isBlank() && !"null".equalsIgnoreCase(identifier))
                    .collect(Collectors.toSet());
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            log.warn("Monitoring API returned {} during alert scope resolution. Returning empty access scope.",
                    exception.getStatusCode().value());
            return Collections.emptySet();
        } catch (ResourceAccessException exception) {
            log.warn("Monitoring API is unavailable during alert scope resolution. Returning empty access scope. Cause: {}",
                    exception.getMessage());
            return Collections.emptySet();
        } catch (RestClientException exception) {
            log.warn("Monitoring API is unavailable during alert scope resolution. Returning empty access scope. Cause: {}",
                    exception.getMessage());
            return Collections.emptySet();
        }
    }

    private String normalizeAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }

        String trimmedValue = authorizationHeader.trim();
        if (trimmedValue.isBlank()) {
            return null;
        }

        if (trimmedValue.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = trimmedValue.substring(7).trim();
            return token.isBlank() ? null : "Bearer " + token;
        }

        return "Bearer " + trimmedValue;
    }
}
