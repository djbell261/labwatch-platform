package com.example.alertengine.service;

import com.example.alertengine.dto.HealthEventMessage;
import com.example.alertengine.entity.Alert;
import com.example.alertengine.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AlertProcessingService {

    private final AlertRepository alertRepository;

    public AlertProcessingService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public void processHealthEvent(HealthEventMessage eventMessage) {
        validate(eventMessage);

        String eventType = eventMessage.getEventType().toUpperCase();
        double value = eventMessage.getMetricValue().doubleValue();

        String alertType;
        String severity;
        String message;
        boolean thresholdExceeded;

        switch (eventType) {
            case "CPU" -> {
                alertType = "CPU";
                thresholdExceeded = value > 90;
                severity = "HIGH";
                message = "CPU usage exceeded threshold";
            }
            case "MEMORY" -> {
                alertType = "MEMORY";
                thresholdExceeded = value > 85;
                severity = "HIGH";
                message = "Memory usage exceeded threshold";
            }
            case "DISK" -> {
                alertType = "DISK";
                thresholdExceeded = value > 95;
                severity = "CRITICAL";
                message = "Disk usage exceeded threshold";
            }
            default -> {
                return;
            }
        }

        Optional<Alert> existingActiveAlert =
                alertRepository.findByMachineIdAndAlertTypeAndStatus(
                        eventMessage.getMachineId(),
                        alertType,
                        "ACTIVE"
                );

        if (thresholdExceeded) {
            if (existingActiveAlert.isEmpty()) {
                Alert alert = new Alert();
                alert.setEventId(eventMessage.getEventId());
                alert.setMachineId(eventMessage.getMachineId());
                alert.setMachineIdentifier(eventMessage.getMachineIdentifier());
                alert.setHostname(eventMessage.getHostname());
                alert.setAlertType(alertType);
                alert.setSeverity(severity);
                alert.setMessage(message);
                alert.setStatus("ACTIVE");
                alertRepository.save(alert);
            }
        } else {
            existingActiveAlert.ifPresent(alert -> {
                alert.setStatus("RESOLVED");
                alert.setResolvedAt(LocalDateTime.now());
                alertRepository.save(alert);
            });
        }
    }

    private void validate(HealthEventMessage eventMessage) {
        if (eventMessage == null) {
            throw new RuntimeException("Health event message cannot be null");
        }
        if (eventMessage.getMachineId() == null) {
            throw new RuntimeException("Machine ID is required");
        }
        if (eventMessage.getMetricValue() == null) {
            throw new RuntimeException("Metric value is required");
        }
        if (eventMessage.getEventType() == null || eventMessage.getEventType().isBlank()) {
            throw new RuntimeException("Event type is required");
        }
    }
}