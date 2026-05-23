package com.example.aiengineservice.dto;

import com.example.aiengineservice.entity.AiInvestigationEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiInvestigationResponse {

    private String investigationId;
    private String incidentId;
    private String incidentGroupKey;
    private String incidentStatus;
    private String alertId;
    private String machineIdentifier;
    private String alertType;
    private String severity;
    private String summary;
    private String likelyCause;
    private String evidence;
    private String contributingFactors;
    private String recommendedChecks;
    private String recommendedAction;
    private String urgencyAssessment;
    private String persistenceAssessment;
    private String monitorNext;
    private String suspectedContributor;
    private String affectedMetrics;
    private Integer confidenceScore;
    private String confidenceLevel;
    private String confidenceReasoning;
    private String baselineSummary;
    private String historicalPatternNotes;
    private List<CorrelationTimelineEntry> correlationTimeline;
    private String confidence;
    private LocalDateTime createdAt;
    private LocalDateTime persistedAt;

    public static AiInvestigationResponse fromEntity(AiInvestigationEntity entity) {
        return new AiInvestigationResponse(
                entity.getInvestigationId(),
                entity.getIncidentId(),
                entity.getIncidentGroupKey(),
                entity.getIncidentStatus(),
                entity.getAlertId(),
                entity.getMachineIdentifier(),
                entity.getAlertType(),
                entity.getSeverity(),
                entity.getSummary(),
                entity.getLikelyCause(),
                entity.getEvidence(),
                entity.getContributingFactors(),
                entity.getRecommendedChecks(),
                entity.getRecommendedAction(),
                entity.getUrgencyAssessment(),
                entity.getPersistenceAssessment(),
                entity.getMonitorNext(),
                entity.getSuspectedContributor(),
                entity.getAffectedMetrics(),
                entity.getConfidenceScore(),
                entity.getConfidenceLevel(),
                entity.getConfidenceReasoning(),
                entity.getBaselineSummary(),
                entity.getHistoricalPatternNotes(),
                entity.getCorrelationTimeline(),
                entity.getConfidence(),
                entity.getCreatedAt(),
                entity.getPersistedAt()
        );
    }
}
