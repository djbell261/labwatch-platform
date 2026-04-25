package com.example.aiengineservice.controller;

import com.example.aiengineservice.entity.Anomaly;
import com.example.aiengineservice.service.AnomalyQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final AnomalyQueryService anomalyQueryService;

    public AnomalyController(AnomalyQueryService anomalyQueryService) {
        this.anomalyQueryService = anomalyQueryService;
    }

    @GetMapping
    public List<Anomaly> getAnomalies(
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) String eventType
    ) {
        if (machineId != null && eventType != null && !eventType.isBlank()) {
            return anomalyQueryService.findByMachineIdAndEventType(machineId, eventType);
        }

        if (machineId != null) {
            return anomalyQueryService.findByMachineId(machineId);
        }

        if (eventType != null && !eventType.isBlank()) {
            return anomalyQueryService.findByEventType(eventType);
        }

        return anomalyQueryService.findAll();
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
