package com.example.alertengine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventMessage {

    private Long alertId;
    private String machineIdentifier;
    private String hostname;
    private String alertType;
    private String severity;
    private String status;
    private Double metricValue;
    private LocalDateTime createdAt;
}
