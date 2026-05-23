package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.CorrelationTimelineEntry;
import com.example.aiengineservice.dto.external.AlertResponse;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.entity.AiInvestigationEntity;
import com.example.aiengineservice.repository.AiInvestigationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class IncidentCorrelationService {

    private final AiInvestigationRepository aiInvestigationRepository;
    private final int groupingWindowMinutes;

    public IncidentCorrelationService(
            AiInvestigationRepository aiInvestigationRepository,
            @Value("${labwatch.ai.incidents.grouping-window-minutes:5}") int groupingWindowMinutes
    ) {
        this.aiInvestigationRepository = aiInvestigationRepository;
        this.groupingWindowMinutes = groupingWindowMinutes;
    }

    public IncidentCorrelationResult correlate(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            List<CorrelationTimelineEntry> timeline
    ) {
        String machineIdentifier = fallback(alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : null);
        LocalDateTime anchor = alertEventMessage != null && alertEventMessage.getCreatedAt() != null
                ? alertEventMessage.getCreatedAt()
                : LocalDateTime.now();
        String suspectedContributor = suspectedContributor(alertEventMessage, context);
        Set<String> affectedMetrics = affectedMetrics(alertEventMessage, context, timeline);
        String groupKey = buildGroupKey(machineIdentifier, suspectedContributor, affectedMetrics);

        List<AiInvestigationEntity> candidates = aiInvestigationRepository
                .findTop20ByMachineIdentifierAndIncidentStatusInOrderByCreatedAtDesc(
                        machineIdentifier,
                        List.of("ACTIVE", "INVESTIGATING", "RECOVERING")
                );

        AiInvestigationEntity matched = candidates.stream()
                .filter(candidate -> withinWindow(candidate.getCreatedAt(), anchor))
                .filter(candidate -> isRelated(candidate, groupKey, suspectedContributor, affectedMetrics))
                .findFirst()
                .orElse(null);

        String incidentId = matched != null && hasText(matched.getIncidentId())
                ? matched.getIncidentId()
                : UUID.randomUUID().toString();
        String status = matched != null && hasText(matched.getIncidentStatus())
                ? nextStatus(matched.getIncidentStatus())
                : "ACTIVE";

        return new IncidentCorrelationResult(
                incidentId,
                groupKey,
                status,
                suspectedContributor,
                String.join(", ", affectedMetrics),
                matched != null ? 1 : 0
        );
    }

    private boolean isRelated(
            AiInvestigationEntity candidate,
            String groupKey,
            String suspectedContributor,
            Set<String> affectedMetrics
    ) {
        if (groupKey.equals(candidate.getIncidentGroupKey())) {
            return true;
        }
        if (hasText(suspectedContributor)
                && hasText(candidate.getSuspectedContributor())
                && processKey(suspectedContributor).equals(processKey(candidate.getSuspectedContributor()))) {
            return true;
        }
        String candidateMetric = normalize(candidate.getAlertType());
        return affectedMetrics.contains(candidateMetric)
                || affectedMetrics.stream().anyMatch(metric -> isComplementary(metric, candidateMetric));
    }

    private boolean withinWindow(LocalDateTime candidateTime, LocalDateTime anchor) {
        if (candidateTime == null || anchor == null) {
            return false;
        }
        long minutes = Math.abs(Duration.between(candidateTime, anchor).toMinutes());
        return minutes <= groupingWindowMinutes;
    }

    private String suspectedContributor(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context
    ) {
        String metric = normalize(alertEventMessage != null ? alertEventMessage.getAlertType() : null);
        ProcessMetricResponse process = "MEMORY".equals(metric) ? context.topMemoryProcess() : context.topCpuProcess();
        if (process == null || !hasText(process.getProcessName())) {
            process = context.topProcess();
        }
        return process != null && hasText(process.getProcessName()) ? process.getProcessName() : "unknown";
    }

    private Set<String> affectedMetrics(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            List<CorrelationTimelineEntry> timeline
    ) {
        Set<String> metrics = new LinkedHashSet<>();
        metrics.add(normalize(alertEventMessage != null ? alertEventMessage.getAlertType() : null));
        if (context.activeAlerts() != null) {
            context.activeAlerts().stream()
                    .map(AlertResponse::getAlertType)
                    .map(this::normalize)
                    .filter(metric -> !"UNKNOWN".equals(metric))
                    .forEach(metrics::add);
        }
        if (timeline != null) {
            timeline.stream()
                    .map(CorrelationTimelineEntry::getMetric)
                    .map(this::normalize)
                    .filter(metric -> !"UNKNOWN".equals(metric))
                    .forEach(metrics::add);
        }
        return metrics;
    }

    private String buildGroupKey(String machineIdentifier, String suspectedContributor, Set<String> affectedMetrics) {
        String process = hasText(suspectedContributor) ? processKey(suspectedContributor) : "unknown";
        String metrics = affectedMetrics.stream().sorted().reduce((left, right) -> left + "+" + right).orElse("UNKNOWN");
        return machineIdentifier + "|" + process + "|" + metrics;
    }

    private boolean isComplementary(String left, String right) {
        return ("CPU".equals(left) && "MEMORY".equals(right))
                || ("MEMORY".equals(left) && "CPU".equals(right))
                || ("MEMORY".equals(left) && "DISK".equals(right))
                || ("DISK".equals(left) && "MEMORY".equals(right));
    }

    private String nextStatus(String currentStatus) {
        String normalized = normalize(currentStatus);
        if ("ACTIVE".equals(normalized)) {
            return "INVESTIGATING";
        }
        return normalized;
    }

    private String processKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String fallback(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record IncidentCorrelationResult(
            String incidentId,
            String incidentGroupKey,
            String incidentStatus,
            String suspectedContributor,
            String affectedMetrics,
            int relatedInvestigationCount
    ) {
    }
}
