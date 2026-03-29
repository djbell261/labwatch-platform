package com.example.monitoringapi.dto.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;



@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
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