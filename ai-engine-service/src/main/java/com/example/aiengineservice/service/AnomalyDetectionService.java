package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AnomalyDetectionResult;
import com.example.aiengineservice.dto.AnomalyEventMessage;
import com.example.aiengineservice.dto.HealthEventMessage;
import com.example.aiengineservice.entity.Anomaly;
import com.example.aiengineservice.kafka.AnomalyEventProducer;
import com.example.aiengineservice.repository.AnomalyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private final Map<String, Deque<Double>> rollingWindows = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> machineFirstSeenAt = new ConcurrentHashMap<>();
    private final Map<String, Boolean> detectionActivated = new ConcurrentHashMap<>();
    private final AnomalyRepository anomalyRepository;
    private final AnomalyEventProducer anomalyEventProducer;
    private final int rollingWindowSize;
    private final int minimumSampleSize;
    private final long minimumBaselineSeconds;
    private final double zScoreThreshold;
    private final long anomalyCooldownSeconds;

    public AnomalyDetectionService(
            AnomalyRepository anomalyRepository,
            AnomalyEventProducer anomalyEventProducer,
            @Value("${app.anomaly.window-size:10}") int rollingWindowSize,
            @Value("${labwatch.ai.anomaly.min-sample-size:20}") int minimumSampleSize,
            @Value("${labwatch.ai.anomaly.min-baseline-seconds:60}") long minimumBaselineSeconds,
            @Value("${labwatch.ai.anomaly.z-score-threshold:3.0}") double zScoreThreshold,
            @Value("${labwatch.ai.anomaly.cooldown-seconds:120}") long anomalyCooldownSeconds
    ) {
        this.anomalyRepository = anomalyRepository;
        this.anomalyEventProducer = anomalyEventProducer;
        this.rollingWindowSize = rollingWindowSize;
        this.minimumSampleSize = minimumSampleSize;
        this.minimumBaselineSeconds = minimumBaselineSeconds;
        this.zScoreThreshold = zScoreThreshold;
        this.anomalyCooldownSeconds = anomalyCooldownSeconds;
    }

    public void processHealthEvent(HealthEventMessage eventMessage) {
        validate(eventMessage);

        String eventType = eventMessage.getEventType().trim().toUpperCase();
        double metricValue = eventMessage.getMetricValue().doubleValue();
        String windowKey = buildWindowKey(eventMessage.getMachineId(), eventType);
        LocalDateTime observedAt = resolveObservedAt(eventMessage);
        LocalDateTime firstSeenAt = machineFirstSeenAt.computeIfAbsent(eventMessage.getMachineId(), ignored -> observedAt);

        Deque<Double> window = rollingWindows.computeIfAbsent(windowKey, ignored -> new ArrayDeque<>());
        AnomalyDetectionResult detectionResult;

        synchronized (window) {
            detectionResult = detect(metricValue, window);
            addValue(window, metricValue);
        }

        if (isWarmingUp(windowKey, eventMessage, firstSeenAt, observedAt, detectionResult.getSampleSize())) {
            return;
        }

        logDetectionActivated(windowKey, eventMessage, observedAt, detectionResult.getSampleSize());

        if (!detectionResult.isAnomalous()) {
            return;
        }

        if (isCoolingDown(eventMessage.getMachineId(), eventType, observedAt)) {
            log.warn(
                    "Skipping anomaly for machine {} metric {} because cooldown is still active.",
                    eventMessage.getMachineIdentifier(),
                    eventType
            );
            return;
        }

        LocalDateTime detectedAt = observedAt;

        String severity = determineSeverity(Math.abs(detectionResult.getZScore()));
        String message = String.format(
                "%s anomaly detected for %s (value=%.2f, baseline=%.2f, z-score=%.2f)",
                eventType,
                eventMessage.getMachineIdentifier(),
                metricValue,
                detectionResult.getRollingAverage(),
                detectionResult.getZScore()
        );

        Anomaly anomaly = new Anomaly();
        anomaly.setAnomalyId(UUID.randomUUID());
        anomaly.setSourceEventId(eventMessage.getEventId());
        anomaly.setMachineId(eventMessage.getMachineId());
        anomaly.setMachineIdentifier(eventMessage.getMachineIdentifier());
        anomaly.setHostname(defaultString(eventMessage.getHostname()));
        anomaly.setEventType(eventType);
        anomaly.setMetricValue(metricValue);
        anomaly.setRollingAverage(detectionResult.getRollingAverage());
        anomaly.setStandardDeviation(detectionResult.getStandardDeviation());
        anomaly.setZScore(detectionResult.getZScore());
        anomaly.setSampleSize(detectionResult.getSampleSize());
        anomaly.setSeverity(severity);
        anomaly.setMessage(message);
        anomaly.setDetectedAt(detectedAt);

        anomalyRepository.save(anomaly);
        anomalyEventProducer.publish(toEventMessage(anomaly));
    }

    private AnomalyDetectionResult detect(double currentValue, Deque<Double> window) {
        int sampleSize = window.size();
        if (sampleSize < minimumSampleSize) {
            return new AnomalyDetectionResult(false, 0.0, 0.0, 0.0, sampleSize);
        }

        double average = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = window.stream()
                .mapToDouble(value -> Math.pow(value - average, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);

        if (standardDeviation == 0.0d) {
            return new AnomalyDetectionResult(false, average, standardDeviation, 0.0, sampleSize);
        }

        double zScore = (currentValue - average) / standardDeviation;
        boolean anomalous = Math.abs(zScore) >= zScoreThreshold;
        return new AnomalyDetectionResult(anomalous, average, standardDeviation, zScore, sampleSize);
    }

    private boolean isWarmingUp(
            String windowKey,
            HealthEventMessage eventMessage,
            LocalDateTime firstSeenAt,
            LocalDateTime observedAt,
            int sampleSize
    ) {
        long baselineAgeSeconds = Math.max(0, Duration.between(firstSeenAt, observedAt).getSeconds());
        boolean baselineReady = baselineAgeSeconds >= minimumBaselineSeconds;
        boolean sampleReady = sampleSize >= minimumSampleSize;

        if (baselineReady && sampleReady) {
            return false;
        }

        log.info(
                "Machine {} metric {} still warming up for anomaly detection (baselineAgeSeconds={}, sampleSize={}, requiredBaselineSeconds={}, requiredSampleSize={}).",
                eventMessage.getMachineIdentifier(),
                eventMessage.getEventType(),
                baselineAgeSeconds,
                sampleSize,
                minimumBaselineSeconds,
                minimumSampleSize
        );
        detectionActivated.remove(windowKey);
        return true;
    }

    private void logDetectionActivated(
            String windowKey,
            HealthEventMessage eventMessage,
            LocalDateTime observedAt,
            int sampleSize
    ) {
        if (Boolean.TRUE.equals(detectionActivated.putIfAbsent(windowKey, Boolean.TRUE))) {
            return;
        }

        long baselineAgeSeconds = Math.max(0, Duration.between(
                machineFirstSeenAt.getOrDefault(eventMessage.getMachineId(), observedAt),
                observedAt
        ).getSeconds());

        log.info(
                "Anomaly detection is now active for machine {} metric {} (baselineAgeSeconds={}, sampleSize={}, zScoreThreshold={}).",
                eventMessage.getMachineIdentifier(),
                eventMessage.getEventType(),
                baselineAgeSeconds,
                sampleSize,
                zScoreThreshold
        );
    }

    private boolean isCoolingDown(Long machineId, String eventType, LocalDateTime detectedAt) {
        return anomalyRepository.findFirstByMachineIdAndEventTypeOrderByDetectedAtDesc(machineId, eventType)
                .map(existing -> {
                    LocalDateTime previousDetectedAt = existing.getDetectedAt();
                    if (previousDetectedAt == null) {
                        return false;
                    }
                    long secondsSincePrevious = Math.max(0, Duration.between(previousDetectedAt, detectedAt).getSeconds());
                    return secondsSincePrevious < anomalyCooldownSeconds;
                })
                .orElse(false);
    }

    private void addValue(Deque<Double> window, double value) {
        if (window.size() >= rollingWindowSize) {
            window.removeFirst();
        }
        window.addLast(value);
    }

    private String buildWindowKey(Long machineId, String eventType) {
        return machineId + ":" + eventType;
    }

    private LocalDateTime resolveObservedAt(HealthEventMessage eventMessage) {
        return eventMessage.getCreatedAt() != null ? eventMessage.getCreatedAt() : LocalDateTime.now();
    }

    private String determineSeverity(double absoluteZScore) {
        if (absoluteZScore >= 4.0) {
            return "CRITICAL";
        }
        if (absoluteZScore >= 3.5) {
            return "HIGH";
        }
        if (absoluteZScore >= 3.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private AnomalyEventMessage toEventMessage(Anomaly anomaly) {
        return new AnomalyEventMessage(
                anomaly.getAnomalyId(),
                anomaly.getSourceEventId(),
                anomaly.getMachineId(),
                anomaly.getMachineIdentifier(),
                anomaly.getHostname(),
                anomaly.getEventType(),
                anomaly.getMetricValue(),
                anomaly.getRollingAverage(),
                anomaly.getStandardDeviation(),
                anomaly.getZScore(),
                anomaly.getSampleSize(),
                anomaly.getSeverity(),
                anomaly.getMessage(),
                anomaly.getDetectedAt()
        );
    }

    private void validate(HealthEventMessage eventMessage) {
        if (eventMessage == null) {
            throw new IllegalArgumentException("Health event message cannot be null");
        }
        if (eventMessage.getMachineId() == null) {
            throw new IllegalArgumentException("Machine ID is required");
        }
        if (eventMessage.getEventId() == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (eventMessage.getEventType() == null || eventMessage.getEventType().isBlank()) {
            throw new IllegalArgumentException("Event type is required");
        }
        if (eventMessage.getMetricValue() == null) {
            throw new IllegalArgumentException("Metric value is required");
        }
        if (eventMessage.getMachineIdentifier() == null || eventMessage.getMachineIdentifier().isBlank()) {
            throw new IllegalArgumentException("Machine identifier is required");
        }
    }

    private String defaultString(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
