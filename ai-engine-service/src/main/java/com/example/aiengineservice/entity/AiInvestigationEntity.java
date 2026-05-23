package com.example.aiengineservice.entity;

import com.example.aiengineservice.dto.CorrelationTimelineEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ai_investigations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiInvestigationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "investigation_id", nullable = false, unique = true)
    private String investigationId;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(name = "incident_group_key", nullable = false, length = 1000)
    private String incidentGroupKey;

    @Column(name = "incident_status", nullable = false)
    private String incidentStatus;

    @Column(name = "alert_id", nullable = false)
    private String alertId;

    @Column(name = "machine_identifier", nullable = false)
    private String machineIdentifier;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false, length = 4000)
    private String summary;

    @Column(name = "likely_cause", nullable = false, length = 4000)
    private String likelyCause;

    @Column(nullable = false, length = 4000)
    private String evidence;

    @Column(name = "contributing_factors", nullable = false, length = 4000)
    private String contributingFactors;

    @Column(name = "recommended_checks", nullable = false, length = 4000)
    private String recommendedChecks;

    @Column(name = "recommended_action", nullable = false, length = 4000)
    private String recommendedAction;

    @Column(name = "urgency_assessment", nullable = false, length = 4000)
    private String urgencyAssessment;

    @Column(name = "persistence_assessment", nullable = false, length = 4000)
    private String persistenceAssessment;

    @Column(name = "monitor_next", nullable = false, length = 4000)
    private String monitorNext;

    @Column(name = "suspected_contributor", nullable = false)
    private String suspectedContributor;

    @Column(name = "affected_metrics", nullable = false, length = 1000)
    private String affectedMetrics;

    @Column(name = "confidence_score", nullable = false)
    private Integer confidenceScore;

    @Column(name = "confidence_level", nullable = false)
    private String confidenceLevel;

    @Column(name = "confidence_reasoning", nullable = false, length = 4000)
    private String confidenceReasoning;

    @Column(name = "baseline_summary", nullable = false, length = 4000)
    private String baselineSummary;

    @Column(name = "historical_pattern_notes", nullable = false, length = 4000)
    private String historicalPatternNotes;

    @Convert(converter = CorrelationTimelineEntryListConverter.class)
    @Column(name = "correlation_timeline", nullable = false, length = 12000)
    private List<CorrelationTimelineEntry> correlationTimeline;

    @Column(nullable = false)
    private String confidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "persisted_at", nullable = false)
    private LocalDateTime persistedAt;

    @PrePersist
    public void prePersist() {
        if (persistedAt == null) {
            persistedAt = LocalDateTime.now();
        }
    }
}
