package com.example.alertengine.controller;


import com.example.alertengine.entity.Alert;
import com.example.alertengine.repository.AlertRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }

    @GetMapping("/active")
    public List<Alert> getActiveAlerts() {
        return alertRepository.findByStatus("ACTIVE");
    }

    @GetMapping("/resolved")
    public List<Alert> getResolvedAlerts() {
        return alertRepository.findByStatus("RESOLVED");
    }

    @GetMapping("/machine/{machineId}")
    public List<Alert> getAlertsByMachine(@PathVariable Long machineId) {
        return alertRepository.findByMachineId(machineId);
    }
}