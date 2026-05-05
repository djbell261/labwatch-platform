package com.example.aiengineservice.controller;

import com.example.aiengineservice.entity.Anomaly;
import com.example.aiengineservice.service.AccessScopeService;
import com.example.aiengineservice.service.AnomalyQueryService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final AnomalyQueryService anomalyQueryService;
    private final AccessScopeService accessScopeService;

    public AnomalyController(AnomalyQueryService anomalyQueryService, AccessScopeService accessScopeService) {
        this.anomalyQueryService = anomalyQueryService;
        this.accessScopeService = accessScopeService;
    }

    @GetMapping
    public List<Anomaly> getAnomalies(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) String machineIdentifier,
            @RequestParam(required = false) String eventType
    ) {
        List<Anomaly> anomalies;
        if (machineIdentifier != null && !machineIdentifier.isBlank()) {
            anomalies = anomalyQueryService.findByMachineIdentifier(machineIdentifier);
        } else if (machineId != null && eventType != null && !eventType.isBlank()) {
            anomalies = anomalyQueryService.findByMachineIdAndEventType(machineId, eventType);
        } else if (machineId != null) {
            anomalies = anomalyQueryService.findByMachineId(machineId);
        } else if (eventType != null && !eventType.isBlank()) {
            anomalies = anomalyQueryService.findByEventType(eventType);
        } else {
            anomalies = anomalyQueryService.findAll();
        }

        if (!accessScopeService.isAuthEnabled()) {
            return anomalies;
        }

        Set<String> accessibleMachines = accessScopeService.resolveAccessibleMachineIdentifiers(authorizationHeader);
        return anomalies.stream()
                .filter(anomaly -> accessibleMachines.contains(anomaly.getMachineIdentifier()))
                .toList();
    }

    @GetMapping("/{id}")
    public Anomaly getById(@PathVariable Long id) {
        return anomalyQueryService.findById(id);
    }

    @GetMapping("/machine/{machineId}")
    public List<Anomaly> getByMachine(@PathVariable Long machineId) {
        return anomalyQueryService.findByMachineId(machineId);
    }
}
