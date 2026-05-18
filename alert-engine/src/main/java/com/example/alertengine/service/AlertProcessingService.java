package com.example.alertengine.service;

import com.example.alertengine.dto.AlertEventMessage;
import com.example.alertengine.dto.HealthEventMessage;
import com.example.alertengine.entity.Alert;
import com.example.alertengine.kafka.AlertEventProducer;
import com.example.alertengine.repository.AlertRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AlertProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AlertProcessingService.class);
    private static final long MIN_ACTIVE_DURATION_SECONDS = 60;

    private final AlertRepository alertRepository;
    private final AlertEventProducer alertEventProducer;
    private final MeterRegistry meterRegistry;

    public AlertProcessingService(
            AlertRepository alertRepository,
            AlertEventProducer alertEventProducer,
            MeterRegistry meterRegistry
    ) {
        this.alertRepository = alertRepository;
        this.alertEventProducer = alertEventProducer;
        this.meterRegistry = meterRegistry;
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
                thresholdExceeded = value > 25;
                severity = "HIGH";
                alertMessage = "CPU usage exceeded threshold";
            }
            case "MEMORY" -> {
                alertType = "MEMORY";
                thresholdExceeded = value > 75;
                severity = "HIGH";
                alertMessage = "Memory usage exceeded threshold";
            }
            case "DISK" -> {
                alertType = "DISK";
                thresholdExceeded = value > 10;
                severity = "CRITICAL";
                alertMessage = "Disk usage exceeded threshold";
            }
            default -> {
                log.info("event=alert_skipped reason=unsupported_event_type eventType={}", eventType);
                return;
            }
        }

        Optional<Alert> existingActiveAlert = alertRepository.findByMachineIdAndAlertTypeAndStatus(
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
            log.info(
                    "event=alert_duplicate_skipped machineIdentifier={} alertType={}",
                    eventMessage.getMachineIdentifier(),
                    alertType
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

        try {
            Alert savedAlert = alertRepository.saveAndFlush(alert);
            meterRegistry.counter("labwatch.alerts.created", "alertType", alertType).increment();
            alertEventProducer.publish(toAlertEventMessage(savedAlert, eventMessage));
            log.info(
                    "event=alert_created machineIdentifier={} alertType={} severity={}",
                    eventMessage.getMachineIdentifier(),
                    alertType,
                    severity
            );
        } catch (DataIntegrityViolationException exception) {
            Alert existingAlert = alertRepository.findFirstByMachineIdAndAlertTypeAndStatus(
                    eventMessage.getMachineId(),
                    alertType,
                    "ACTIVE"
            ).orElse(null);
            log.info(
                    "event=alert_duplicate_race machineIdentifier={} alertType={} existingAlertId={}",
                    eventMessage.getMachineIdentifier(),
                    alertType,
                    existingAlert != null ? existingAlert.getId() : "unknown"
            );
        }
    }

    private void handleAlertResolution(
            HealthEventMessage eventMessage,
            String alertType,
            Optional<Alert> existingActiveAlert
    ) {
        if (existingActiveAlert.isEmpty()) {
            log.info(
                    "event=alert_resolution_skipped reason=no_active_alert machineIdentifier={} alertType={}",
                    eventMessage.getMachineIdentifier(),
                    alertType
            );
            return;
        }

        Alert alert = existingActiveAlert.get();
        LocalDateTime now = LocalDateTime.now();
        long activeDurationSeconds = Duration.between(alert.getCreatedAt(), now).getSeconds();

        if (activeDurationSeconds < MIN_ACTIVE_DURATION_SECONDS) {
            log.info(
                    "event=alert_resolution_delayed machineIdentifier={} alertType={} activeDurationSeconds={} minimumActiveDurationSeconds={}",
                    eventMessage.getMachineIdentifier(),
                    alertType,
                    activeDurationSeconds,
                    MIN_ACTIVE_DURATION_SECONDS
            );
            return;
        }

        alert.setStatus("RESOLVED");
        alert.setResolvedAt(now);

        alertRepository.save(alert);
        meterRegistry.counter("labwatch.alerts.resolved", "alertType", alertType).increment();
        log.info(
                "event=alert_resolved machineIdentifier={} alertType={}",
                eventMessage.getMachineIdentifier(),
                alertType
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

    private AlertEventMessage toAlertEventMessage(Alert alert, HealthEventMessage eventMessage) {
        return new AlertEventMessage(
                alert.getId(),
                alert.getMachineIdentifier(),
                alert.getHostname(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getStatus(),
                eventMessage.getMetricValue() != null ? eventMessage.getMetricValue().doubleValue() : null,
                alert.getCreatedAt()
        );
    }
}
