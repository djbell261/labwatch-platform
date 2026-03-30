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
        String alertMessage;
        boolean thresholdExceeded;

        switch (eventType) {
            case "CPU" -> {
                alertType = "CPU";
                thresholdExceeded = value > 90;
                severity = "HIGH";
                alertMessage = "CPU usage exceeded threshold";
            }
            case "MEMORY" -> {
                alertType = "MEMORY";
                thresholdExceeded = value > 85;
                severity = "HIGH";
                alertMessage = "Memory usage exceeded threshold";
            }
            case "DISK" -> {
                alertType = "DISK";
                thresholdExceeded = value > 95;
                severity = "CRITICAL";
                alertMessage = "Disk usage exceeded threshold";
            }
            default -> {
                System.out.println("Skipping unsupported event type: " + eventType);
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
            handleActiveAlertCreation(eventMessage, alertType, severity, alertMessage, existingActiveAlert);
        } else {
            handleAlertResolution(eventMessage, alertType, existingActiveAlert);
        }
    }

    private void handleActiveAlertCreation(
            HealthEventMessage eventMessage,
            String alertType,
            String severity,
            String alertMessage,
            Optional<Alert> existingActiveAlert
    ) {
        if (existingActiveAlert.isPresent()) {
            System.out.println(
                    "Duplicate alert skipped for machine " +
                            eventMessage.getMachineIdentifier() +
                            " and alert type " + alertType
            );
            return;
        }

        Alert alert = new Alert();
        alert.setEventId(eventMessage.getEventId());
        alert.setMachineId(eventMessage.getMachineId());
        alert.setMachineIdentifier(eventMessage.getMachineIdentifier());
        alert.setHostname(eventMessage.getHostname());
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setMessage(alertMessage);
        alert.setStatus("ACTIVE");
        alert.setCreatedAt(LocalDateTime.now());

        alertRepository.save(alert);

        System.out.println(
                "Created ACTIVE alert for machine " +
                        eventMessage.getMachineIdentifier() +
                        " and alert type " + alertType
        );
    }

    private void handleAlertResolution(
            HealthEventMessage eventMessage,
            String alertType,
            Optional<Alert> existingActiveAlert
    ) {
        if (existingActiveAlert.isEmpty()) {
            System.out.println(
                    "No ACTIVE alert to resolve for machine " +
                            eventMessage.getMachineIdentifier() +
                            " and alert type " + alertType
            );
            return;
        }

        Alert alert = existingActiveAlert.get();
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(LocalDateTime.now());

        alertRepository.save(alert);

        System.out.println(
                "Resolved alert for machine " +
                        eventMessage.getMachineIdentifier() +
                        " and alert type " + alertType
        );
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