package com.example.alertengine.controller;

import com.example.alertengine.config.SecurityConfig;
import com.example.alertengine.entity.Alert;
import com.example.alertengine.repository.AlertRepository;
import com.example.alertengine.service.AccessScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertController.class)
@Import(SecurityConfig.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertRepository alertRepository;

    @MockBean
    private AccessScopeService accessScopeService;

    @Test
    void authDisabledReturnsAlertsWithoutCallingMonitoringApi() throws Exception {
        when(alertRepository.findAll()).thenReturn(List.of(alert(1L, "machine-a", "ACTIVE")));
        when(accessScopeService.isAuthEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].machineIdentifier").value("machine-a"));

        verify(accessScopeService, never()).resolveAccessibleMachineIdentifiers(anyString());
        verify(accessScopeService, never()).resolveAccessibleMachineIdentifiers(isNull());
    }

    @Test
    void authEnabledWithValidTokenFiltersAlertsByAccessibleMachines() throws Exception {
        when(alertRepository.findAll()).thenReturn(List.of(
                alert(1L, "machine-a", "ACTIVE"),
                alert(2L, "machine-b", "ACTIVE")
        ));
        when(accessScopeService.isAuthEnabled()).thenReturn(true);
        when(accessScopeService.resolveAccessibleMachineIdentifiers("Bearer valid-token"))
                .thenReturn(Set.of("machine-b"));

        mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].machineIdentifier").value("machine-b"));
    }

    @Test
    void authEnabledWithMissingTokenDoesNotCrashAndReturnsEmptyList() throws Exception {
        when(alertRepository.findAll()).thenReturn(List.of(alert(1L, "machine-a", "ACTIVE")));
        when(accessScopeService.isAuthEnabled()).thenReturn(true);
        when(accessScopeService.resolveAccessibleMachineIdentifiers(null)).thenReturn(Set.of());

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void alertsEndpointReturns200WhenAccessScopeResolutionFallsBackToEmptyList() throws Exception {
        when(alertRepository.findAll()).thenReturn(List.of(alert(1L, "machine-a", "ACTIVE")));
        when(accessScopeService.isAuthEnabled()).thenReturn(true);
        when(accessScopeService.resolveAccessibleMachineIdentifiers("Bearer invalid-token")).thenReturn(Set.of());

        mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void machineIdentifierFilterIsAppliedAfterOwnershipFiltering() throws Exception {
        when(alertRepository.findByStatus("ACTIVE")).thenReturn(List.of(
                alert(1L, "machine-a", "ACTIVE"),
                alert(2L, "machine-b", "ACTIVE"),
                alert(3L, "machine-c", "ACTIVE")
        ));
        when(accessScopeService.isAuthEnabled()).thenReturn(true);
        when(accessScopeService.resolveAccessibleMachineIdentifiers("Bearer valid-token"))
                .thenReturn(Set.of("machine-a", "machine-b"));

        mockMvc.perform(
                        get("/api/alerts/active")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                                .param("machineIdentifier", "machine-b")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].machineIdentifier").value("machine-b"));
    }

    @Test
    void resolvedAlertsEndpointReturns200WhenScopeIsEmpty() throws Exception {
        when(alertRepository.findByStatus("RESOLVED")).thenReturn(List.of(alert(1L, "machine-a", "RESOLVED")));
        when(accessScopeService.isAuthEnabled()).thenReturn(true);
        when(accessScopeService.resolveAccessibleMachineIdentifiers("Bearer valid-token")).thenReturn(Set.of());

        mockMvc.perform(get("/api/alerts/resolved").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Alert alert(Long id, String machineIdentifier, String status) {
        Alert alert = new Alert();
        alert.setId(id);
        alert.setEventId(UUID.randomUUID());
        alert.setMachineId(id);
        alert.setMachineIdentifier(machineIdentifier);
        alert.setHostname(machineIdentifier + ".local");
        alert.setAlertType("CPU");
        alert.setSeverity("HIGH");
        alert.setMessage("CPU usage exceeded threshold");
        alert.setStatus(status);
        alert.setCreatedAt(LocalDateTime.now());
        return alert;
    }
}
