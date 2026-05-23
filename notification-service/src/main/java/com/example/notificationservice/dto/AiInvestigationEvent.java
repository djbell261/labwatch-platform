package com.example.notificationservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiInvestigationEvent {

    private UUID investigationId;
    private String incidentId;
    private String incidentGroupKey;
    private String incidentStatus;
    private Long alertId;
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
    private String confidence;
    private LocalDateTime createdAt;

    public AiInvestigationEvent(
            UUID investigationId,
            Long alertId,
            String machineIdentifier,
            String alertType,
            String severity,
            String summary,
            String likelyCause,
            String evidence,
            String contributingFactors,
            String recommendedChecks,
            String recommendedAction,
            String urgencyAssessment,
            String persistenceAssessment,
            String monitorNext,
            String confidence,
            LocalDateTime createdAt
    ) {
        this.investigationId = investigationId;
        this.incidentId = investigationId != null ? investigationId.toString() : null;
        this.incidentGroupKey = "legacy-group";
        this.incidentStatus = "ACTIVE";
        this.alertId = alertId;
        this.machineIdentifier = machineIdentifier;
        this.alertType = alertType;
        this.severity = severity;
        this.summary = summary;
        this.likelyCause = likelyCause;
        this.evidence = evidence;
        this.contributingFactors = contributingFactors;
        this.recommendedChecks = recommendedChecks;
        this.recommendedAction = recommendedAction;
        this.urgencyAssessment = urgencyAssessment;
        this.persistenceAssessment = persistenceAssessment;
        this.monitorNext = monitorNext;
        this.suspectedContributor = "unknown";
        this.affectedMetrics = alertType;
        this.confidenceScore = null;
        this.confidenceLevel = confidence;
        this.confidenceReasoning = "";
        this.baselineSummary = "";
        this.historicalPatternNotes = "";
        this.confidence = confidence;
        this.createdAt = createdAt;
    }
}
