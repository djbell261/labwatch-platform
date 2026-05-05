package com.example.aiengineservice.dto;

import com.example.aiengineservice.entity.AiInvestigationEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiInvestigationResponse {

    private String investigationId;
    private String alertId;
    private String machineIdentifier;
    private String alertType;
    private String severity;
    private String summary;
    private String likelyCause;
    private String recommendedAction;
    private String confidence;
    private LocalDateTime createdAt;
    private LocalDateTime persistedAt;

    public static AiInvestigationResponse fromEntity(AiInvestigationEntity entity) {
        return new AiInvestigationResponse(
                entity.getInvestigationId(),
                entity.getAlertId(),
                entity.getMachineIdentifier(),
                entity.getAlertType(),
                entity.getSeverity(),
                entity.getSummary(),
                entity.getLikelyCause(),
                entity.getRecommendedAction(),
                entity.getConfidence(),
                entity.getCreatedAt(),
                entity.getPersistedAt()
        );
    }
}
