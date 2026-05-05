package com.example.aiengineservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiInvestigationEvent {

    private UUID investigationId;
    private Long alertId;
    private String machineIdentifier;
    private String alertType;
    private String severity;
    private String summary;
    private String likelyCause;
    private String recommendedAction;
    private String confidence;
    private LocalDateTime createdAt;
}
