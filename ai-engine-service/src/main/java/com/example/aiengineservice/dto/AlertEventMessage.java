package com.example.aiengineservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertEventMessage {

    private Long alertId;
    private String machineIdentifier;
    private String hostname;
    private String alertType;
    private String severity;
    private String status;
    private Double metricValue;
    private String anomalyId;
    private Double zScore;
    private String message;
    private LocalDateTime createdAt;
}
