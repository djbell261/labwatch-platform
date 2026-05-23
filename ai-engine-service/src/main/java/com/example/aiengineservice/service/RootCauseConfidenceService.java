package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.CorrelationTimelineEntry;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class RootCauseConfidenceService {

    public ConfidenceScore score(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            MachineBehaviorProfileService.MachineBehaviorProfile profile,
            IncidentCorrelationService.IncidentCorrelationResult incident,
            List<CorrelationTimelineEntry> timeline
    ) {
        int score = 35;
        StringBuilder factors = new StringBuilder();

        ProcessMetricResponse topProcess = dominantProcess(alertEventMessage, context);
        if (topProcess != null && hasText(topProcess.getProcessName())) {
            score += 15;
            append(factors, topProcess.getProcessName() + " is the leading observed contributor.");
        } else {
            score -= 12;
            append(factors, "Process metrics are missing, so attribution is less certain.");
        }

        Double processShare = dominantProcessShare(alertEventMessage, topProcess);
        if (processShare != null && processShare >= 50.0) {
            score += 18;
            append(factors, "The suspected process dominates the affected resource.");
        } else if (processShare != null && processShare >= 25.0) {
            score += 10;
            append(factors, "The suspected process has a meaningful share of the affected resource.");
        } else if (processShare != null) {
            score -= 6;
            append(factors, "The suspected process is not a dominant resource consumer.");
        }

        if (timeline != null && timeline.stream().anyMatch(this::isProcessSpike)) {
            score += 10;
            append(factors, "Process spike evidence appears in the correlation timeline.");
        }
        if (context.activeAlerts() != null && context.activeAlerts().size() > 1) {
            score += 8;
            append(factors, "Multiple related active alerts are present on the same machine.");
        }
        if (context.recentAnomalies() != null && !context.recentAnomalies().isEmpty()) {
            score += 8;
            append(factors, "Recent anomalies support that this was abnormal behavior.");
        }
        if (profile != null && profile.repeatedTopProcess()) {
            score += 10;
            append(factors, "The same process appears repeatedly in recent machine history.");
        }
        if (profile != null && profile.unusualForMachine()) {
            score += 8;
            append(factors, "The metric is unusual relative to this machine's recent baseline.");
        }
        if (incident != null && incident.relatedInvestigationCount() > 0) {
            score += 7;
            append(factors, "This event correlated with an existing incident group.");
        }
        if (timeline == null || timeline.size() < 2) {
            score -= 10;
            append(factors, "The timeline has limited supporting evidence.");
        }
        if (context.activeAlerts() != null && context.activeAlerts().size() >= 3) {
            score -= 5;
            append(factors, "Several competing alerts may blur the primary contributor.");
        }

        int boundedScore = Math.max(15, Math.min(92, score));
        String level = boundedScore >= 75 ? "HIGH" : boundedScore >= 50 ? "MEDIUM" : "LOW";
        return new ConfidenceScore(boundedScore, level, factors.toString());
    }

    private ProcessMetricResponse dominantProcess(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context
    ) {
        String metric = normalize(alertEventMessage != null ? alertEventMessage.getAlertType() : null);
        if ("MEMORY".equals(metric)) {
            return context.topMemoryProcess();
        }
        if ("CPU".equals(metric)) {
            return context.topCpuProcess();
        }
        return context.topProcess();
    }

    private Double dominantProcessShare(AlertEventMessage alertEventMessage, ProcessMetricResponse process) {
        if (process == null) {
            return null;
        }
        String metric = normalize(alertEventMessage != null ? alertEventMessage.getAlertType() : null);
        if ("MEMORY".equals(metric)) {
            return process.getMemoryPercent();
        }
        if ("CPU".equals(metric)) {
            return process.getCpuPercent();
        }
        return process.getCpuPercent() != null ? process.getCpuPercent() : process.getMemoryPercent();
    }

    private boolean isProcessSpike(CorrelationTimelineEntry entry) {
        return entry != null && "PROCESS_SPIKE".equalsIgnoreCase(entry.getType());
    }

    private void append(StringBuilder builder, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append("- ").append(line);
    }

    private String normalize(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ConfidenceScore(int score, String level, String reasoning) {
        public String display() {
            return level + " (" + score + "%)";
        }
    }
}
