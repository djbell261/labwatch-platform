package com.example.alertengine.controller;


import com.example.alertengine.entity.Alert;
import com.example.alertengine.repository.AlertRepository;
import com.example.alertengine.service.AccessScopeService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;
    private final AccessScopeService accessScopeService;

    public AlertController(AlertRepository alertRepository, AccessScopeService accessScopeService) {
        this.alertRepository = alertRepository;
        this.accessScopeService = accessScopeService;
    }

    @GetMapping
    public List<Alert> getAllAlerts(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) String machineIdentifier
    ) {
        List<Alert> alerts = alertRepository.findAll();
        return filterAlerts(alerts, authorizationHeader, machineIdentifier);
    }

    @GetMapping("/active")
    public List<Alert> getActiveAlerts(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) String machineIdentifier
    ) {
        return filterAlerts(alertRepository.findByStatus("ACTIVE"), authorizationHeader, machineIdentifier);
    }

    @GetMapping("/resolved")
    public List<Alert> getResolvedAlerts(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) String machineIdentifier
    ) {
        return filterAlerts(alertRepository.findByStatus("RESOLVED"), authorizationHeader, machineIdentifier);
    }

    @GetMapping("/machine/{machineId}")
    public List<Alert> getAlertsByMachine(@PathVariable Long machineId) {
        return alertRepository.findByMachineId(machineId);
    }

    private List<Alert> filterAlerts(List<Alert> alerts, String authorizationHeader, String machineIdentifier) {
        if (!accessScopeService.isAuthEnabled()) {
            return applyMachineIdentifierFilter(alerts, machineIdentifier);
        }

        Set<String> accessibleMachines = accessScopeService.resolveAccessibleMachineIdentifiers(authorizationHeader);
        List<Alert> ownershipFiltered = alerts.stream()
                .filter(alert -> accessibleMachines.contains(alert.getMachineIdentifier()))
                .toList();
        return applyMachineIdentifierFilter(ownershipFiltered, machineIdentifier);
    }

    private List<Alert> applyMachineIdentifierFilter(List<Alert> alerts, String machineIdentifier) {
        if (machineIdentifier == null || machineIdentifier.isBlank()) {
            return alerts;
        }

        return alerts.stream()
                .filter(alert -> machineIdentifier.equalsIgnoreCase(alert.getMachineIdentifier()))
                .toList();
    }
}
