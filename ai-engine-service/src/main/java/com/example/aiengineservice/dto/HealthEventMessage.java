package com.example.aiengineservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthEventMessage {

    private UUID eventId;
    private Long machineId;
    private String machineIdentifier;
    private String hostname;
    private String eventType;
    private BigDecimal metricValue;
    private String status;
    private String message;
    private LocalDateTime createdAt;
}
