package com.example.aiengineservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyPromotedAlertEvent {

    private Long alertId;
    private String anomalyId;
    private String machineIdentifier;
    private String hostname;
    private String alertType;
    private String severity;
    private String status;
    private Double metricValue;
    private Double zScore;
    private String message;
    private LocalDateTime createdAt;
}
