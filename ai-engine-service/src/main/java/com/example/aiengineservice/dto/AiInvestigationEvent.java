package com.example.aiengineservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
    private List<CorrelationTimelineEntry> correlationTimeline;
    private String confidence;
    private LocalDateTime createdAt;
}
