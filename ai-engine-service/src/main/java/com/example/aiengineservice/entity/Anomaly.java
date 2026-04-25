package com.example.aiengineservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "anomaly")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anomaly_id", nullable = false, unique = true)
    private UUID anomalyId;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "machine_identifier", nullable = false)
    private String machineIdentifier;

    @Column(nullable = false)
    private String hostname;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Column(name = "rolling_average", nullable = false)
    private Double rollingAverage;

    @Column(name = "standard_deviation", nullable = false)
    private Double standardDeviation;

    @Column(name = "z_score", nullable = false)
    private Double zScore;

    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String message;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @PrePersist
    public void prePersist() {
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
    }
}
