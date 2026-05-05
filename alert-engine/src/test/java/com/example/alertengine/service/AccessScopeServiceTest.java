package com.example.alertengine.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessScopeServiceTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec() {
        return mock(RestClient.RequestHeadersUriSpec.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RestClient.RequestHeadersSpec requestHeadersSpec() {
        return mock(RestClient.RequestHeadersSpec.class);
    }

    @Test
    void authDisabledReturnsEmptyScopeWithoutCallingMonitoringApi() {
        RestClient restClient = mock(RestClient.class);
        AccessScopeService service = new AccessScopeService(false, restClient);

        Set<String> result = service.resolveAccessibleMachineIdentifiers(null);

        assertThat(result).isEmpty();
        verify(restClient, never()).get();
    }

    @Test
    void authEnabledWithMissingTokenReturnsEmptyScopeWithoutCallingMonitoringApi() {
        RestClient restClient = mock(RestClient.class);
        AccessScopeService service = new AccessScopeService(true, restClient);

        Set<String> result = service.resolveAccessibleMachineIdentifiers("   ");

        assertThat(result).isEmpty();
        verify(restClient, never()).get();
    }

    @Test
    void authEnabledWithValidTokenReturnsAccessibleMachineIdentifiers() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec requestHeadersUriSpec = requestHeadersUriSpec();
        RestClient.RequestHeadersSpec requestHeadersSpec = requestHeadersSpec();
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        AccessScopeService service = new AccessScopeService(true, restClient);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/api/v1/machines")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer test-token"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(
                Map.of("machineIdentifier", "machine-a"),
                Map.of("machineIdentifier", "machine-b")
        ));

        Set<String> result = service.resolveAccessibleMachineIdentifiers("Bearer test-token");

        assertThat(result).containsExactlyInAnyOrder("machine-a", "machine-b");
    }

    @Test
    void monitoringApiForbiddenReturnsEmptyScopeWithoutThrowing() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec requestHeadersUriSpec = requestHeadersUriSpec();
        RestClient.RequestHeadersSpec requestHeadersSpec = requestHeadersSpec();
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        AccessScopeService service = new AccessScopeService(true, restClient);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/api/v1/machines")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer test-token"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null));

        Set<String> result = service.resolveAccessibleMachineIdentifiers("test-token");

        assertThat(result).isEmpty();
    }

    @Test
    void monitoringApiUnavailableReturnsEmptyScopeWithoutThrowing() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec requestHeadersUriSpec = requestHeadersUriSpec();
        RestClient.RequestHeadersSpec requestHeadersSpec = requestHeadersSpec();
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        AccessScopeService service = new AccessScopeService(true, restClient);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/api/v1/machines")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer test-token"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        Set<String> result = service.resolveAccessibleMachineIdentifiers("Bearer test-token");

        assertThat(result).isEmpty();
    }
}
