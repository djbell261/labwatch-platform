package com.example.aiengineservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AccessScopeService {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> MACHINE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final boolean authEnabled;
    private final RestClient monitoringApiClient;

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

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public Set<String> resolveAccessibleMachineIdentifiers(String authorizationHeader) {
        if (!authEnabled) {
            return Set.of();
        }

        List<Map<String, Object>> response = monitoringApiClient.get()
                .uri("/api/v1/machines")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .body(MACHINE_LIST_TYPE);

        if (response == null) {
            return Set.of();
        }

        return response.stream()
                .map(machine -> String.valueOf(machine.get("machineIdentifier")))
                .filter(identifier -> identifier != null && !identifier.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }
}
