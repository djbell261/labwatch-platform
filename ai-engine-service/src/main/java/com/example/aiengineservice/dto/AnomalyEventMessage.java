package com.example.aiengineservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyEventMessage {

    private UUID anomalyId;
    private UUID sourceEventId;
    private Long machineId;
    private String machineIdentifier;
    private String hostname;
    private String eventType;
    private Double metricValue;
    private Double rollingAverage;
    private Double standardDeviation;
    private Double zScore;
    private Integer sampleSize;
    private String severity;
    private String message;
    private LocalDateTime detectedAt;
}
