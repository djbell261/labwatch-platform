package com.example.monitoringapi.dto.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class HealthEventResponse {

    private Long id;
    private UUID eventId;
    private String machineIdentifier;
    private String hostname;
    private String eventType;
    private BigDecimal metricValue;
    private String status;
    private String message;
    private LocalDateTime createdAt;


}