package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.CorrelationTimelineEntry;
import com.example.aiengineservice.dto.external.AlertResponse;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import com.example.aiengineservice.entity.Anomaly;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class AiInvestigationTriageComposer {

    private final KnownProcessEnrichmentService processEnrichmentService;

    AiInvestigationTriageComposer() {
        this(new KnownProcessEnrichmentService());
    }

    @Autowired
    public AiInvestigationTriageComposer(KnownProcessEnrichmentService processEnrichmentService) {
        this.processEnrichmentService = processEnrichmentService;
    }

    public InvestigationTriage compose(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            String providerSummary,
            List<CorrelationTimelineEntry> correlationTimeline
    ) {
        return compose(alertEventMessage, context, providerSummary, correlationTimeline, null, null, null);
    }

    public InvestigationTriage compose(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            String providerSummary,
            List<CorrelationTimelineEntry> correlationTimeline,
            MachineBehaviorProfileService.MachineBehaviorProfile profile,
            IncidentCorrelationService.IncidentCorrelationResult incident,
            RootCauseConfidenceService.ConfidenceScore confidenceScore
    ) {
        String metric = normalize(alertEventMessage != null ? alertEventMessage.getAlertType() : null);
        String severity = normalize(alertEventMessage != null ? alertEventMessage.getSeverity() : null);
        Double alertValue = alertEventMessage != null ? alertEventMessage.getMetricValue() : null;
        TelemetrySnapshotDetailResponse latestTelemetry = context.latestTelemetry();
        ProcessMetricResponse topCpuProcess = context.topCpuProcess();
        ProcessMetricResponse topMemoryProcess = context.topMemoryProcess();

        String trendAssessment = assessTrend(metric, alertValue, latestTelemetry, context.recentTelemetry());
        List<String> evidence = buildEvidence(metric, alertValue, latestTelemetry, context, topCpuProcess, topMemoryProcess, confidenceScore);
        List<String> contributingFactors = buildContributingFactors(metric, context, topCpuProcess, topMemoryProcess, profile, incident);
        List<String> checks = buildRecommendedChecks(metric, context, topCpuProcess, topMemoryProcess);
        List<String> actions = buildRecommendedActions(metric, trendAssessment, context, topCpuProcess, topMemoryProcess);
        List<String> monitorNext = buildMonitorNext(metric, context, trendAssessment);
        List<String> timelineSummary = buildTimelineSummary(correlationTimeline);
        String likelyCause = buildLikelyCause(metric, context, topCpuProcess, topMemoryProcess, trendAssessment, confidenceScore);
        String urgency = buildUrgency(severity, trendAssessment, context.activeAlerts());
        String summary = providerSummary != null && !providerSummary.isBlank()
                ? providerSummary.trim()
                : buildDeterministicSummary(
                        metric,
                        severity,
                        likelyCause,
                        trendAssessment,
                        topCpuProcess,
                        topMemoryProcess,
                        profile,
                        incident,
                        confidenceScore,
                        checks
                );

        return new InvestigationTriage(
                summary,
                likelyCause,
                joinBullets(evidence),
                joinBullets(contributingFactors),
                joinBullets(checks),
                joinBullets(actions),
                urgency,
                trendAssessment,
                joinBullets(monitorNext),
                joinBullets(timelineSummary)
        );
    }

    private String buildDeterministicSummary(
            String metric,
            String severity,
            String likelyCause,
            String trendAssessment,
            ProcessMetricResponse topCpuProcess,
            ProcessMetricResponse topMemoryProcess,
            MachineBehaviorProfileService.MachineBehaviorProfile profile,
            IncidentCorrelationService.IncidentCorrelationResult incident,
            RootCauseConfidenceService.ConfidenceScore confidenceScore,
            List<String> checks
    ) {
        ProcessMetricResponse process = "MEMORY".equals(metric) ? topMemoryProcess : topCpuProcess;
        ProcessInsight insight = processInsight(process);

        String happening = "Your " + metric.toLowerCase(Locale.ROOT) + " usage is elevated because "
                + plainCause(likelyCause) + ".";
        String why = insight != null
                ? "This usually happens when " + joinPlain(insight.likelyCauses()) + "."
                : defaultWhy(metric);
        String seriousness = seriousnessSentence(metric, severity, trendAssessment, profile, incident, confidenceScore);
        List<String> nextSteps = checks == null || checks.isEmpty()
                ? defaultChecks(metric)
                : checks.stream().limit(3).toList();

        return """
                WHAT'S HAPPENING
                %s

                WHY THIS HAPPENS
                %s

                IS IT SERIOUS?
                %s

                WHAT TO DO NEXT
                Recommended checks:
                %s
                """.formatted(
                happening,
                why,
                seriousness,
                joinBullets(nextSteps)
        ).trim();
    }

    private String buildLikelyCause(
            String metric,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            ProcessMetricResponse topCpuProcess,
            ProcessMetricResponse topMemoryProcess,
            String trendAssessment,
            RootCauseConfidenceService.ConfidenceScore confidenceScore
    ) {
        if ("CPU".equals(metric)) {
            ProcessInsight insight = processInsight(topCpuProcess);
            if (insight != null) {
                return insight.humanExplanation().toLowerCase(Locale.ROOT);
            }
            if (topCpuProcess != null && hasText(topCpuProcess.getProcessName())) {
                return topCpuProcess.getProcessName() + " appearing correlated as the leading observed CPU consumer";
            }
            return "short-term compute pressure from a workload spike or background activity";
        }

        if ("MEMORY".equals(metric)) {
            ProcessInsight insight = processInsight(topMemoryProcess);
            if (insight != null) {
                return insight.humanExplanation().toLowerCase(Locale.ROOT);
            }
            if (topMemoryProcess != null && hasText(topMemoryProcess.getProcessName())) {
                return topMemoryProcess.getProcessName() + " appearing correlated with a large share of memory";
            }
            return "high memory pressure from application growth, too many open workloads, or a possible leak";
        }

        if ("DISK".equals(metric)) {
            return "storage growth from logs, Docker images or volumes, database files, build artifacts, or cache directories";
        }

        return "sustained resource pressure that still needs operator verification";
    }

    private List<String> buildEvidence(
            String metric,
            Double alertValue,
            TelemetrySnapshotDetailResponse latestTelemetry,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            ProcessMetricResponse topCpuProcess,
            ProcessMetricResponse topMemoryProcess,
            RootCauseConfidenceService.ConfidenceScore confidenceScore
    ) {
        List<String> evidence = new ArrayList<>();
        if (alertValue != null) {
            evidence.add(title(metric) + " alert value reached " + formatPercent(alertValue) + ".");
        }

        Double currentValue = currentMetricValue(metric, latestTelemetry);
        if (currentValue != null) {
            evidence.add("Current " + metric.toLowerCase(Locale.ROOT) + " reading is " + formatPercent(currentValue) + ".");
        }

        if (context.recentMetricTrend() != null && !context.recentMetricTrend().contains("unavailable")) {
            evidence.add(context.recentMetricTrend() + ".");
        }

        if ("CPU".equals(metric) && topCpuProcess != null && hasText(topCpuProcess.getProcessName())) {
            evidence.add(processEvidenceLabel(topCpuProcess) + " is the top CPU contributor" + formatPercent(topCpuProcess.getCpuPercent(), " CPU") + ".");
        }

        if ("MEMORY".equals(metric) && topMemoryProcess != null && hasText(topMemoryProcess.getProcessName())) {
            evidence.add(processEvidenceLabel(topMemoryProcess) + " is the top memory contributor" + formatPercent(topMemoryProcess.getMemoryPercent(), " memory") + ".");
        }

        if (context.recentAnomalies() != null && !context.recentAnomalies().isEmpty()) {
            Anomaly anomaly = context.recentAnomalies().get(0);
            evidence.add("Recent anomaly history also flagged " + normalize(anomaly.getEventType()).toLowerCase(Locale.ROOT)
                    + " behavior at " + formatPercent(anomaly.getMetricValue()) + " with severity "
                    + normalize(anomaly.getSeverity()) + ".");
        }

        if (confidenceScore != null) {
            evidence.add("Root-cause confidence is " + confidenceScore.level().toLowerCase(Locale.ROOT)
                    + " (" + confidenceScore.score() + "%), based on available correlation signals rather than certainty.");
        }

        if (("CPU".equals(metric) || "MEMORY".equals(metric))
                && (topCpuProcess == null || !hasText(topCpuProcess.getProcessName()))
                && (topMemoryProcess == null || !hasText(topMemoryProcess.getProcessName()))) {
            evidence.add("Process-level evidence is unavailable, so confirm with process metrics before treating any single process as the cause.");
        }

        return evidence;
    }

    private List<String> buildContributingFactors(
            String metric,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            ProcessMetricResponse topCpuProcess,
            ProcessMetricResponse topMemoryProcess,
            MachineBehaviorProfileService.MachineBehaviorProfile profile,
            IncidentCorrelationService.IncidentCorrelationResult incident
    ) {
        List<String> factors = new ArrayList<>();
        List<String> activeAlertTypes = context.activeAlerts().stream()
                .map(AlertResponse::getAlertType)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .distinct()
                .toList();

        if (activeAlertTypes.contains("CPU") && activeAlertTypes.contains("MEMORY")) {
            factors.add("CPU and memory pressure together often point to a resource-heavy process, browser workload, development tooling, or JVM pressure.");
        }
        if (activeAlertTypes.contains("MEMORY") && activeAlertTypes.contains("DISK")) {
            factors.add("Memory and disk pressure together can indicate swap activity, log growth, cache expansion, or database/container pressure.");
        }
        if (activeAlertTypes.contains("CPU") && activeAlertTypes.contains("DISK")) {
            factors.add("CPU and disk pressure together can reflect indexing, builds, heavy writes, or log churn.");
        }

        ProcessInsight topCpuInsight = processInsight(topCpuProcess);
        if ("CPU".equals(metric) && topCpuInsight != null) {
            factors.add("Likely causes include " + joinPlain(topCpuInsight.likelyCauses()) + ".");
        }

        ProcessInsight topMemoryInsight = processInsight(topMemoryProcess);
        if ("MEMORY".equals(metric) && topMemoryInsight != null) {
            factors.add("Likely causes include " + joinPlain(topMemoryInsight.likelyCauses()) + ".");
        }

        if ("DISK".equals(metric)) {
            factors.add("Common contributors include logs, Docker volumes or images, database growth, build artifacts, and cache or temp files.");
        }

        if (incident != null && hasText(incident.affectedMetrics())) {
            factors.add("Grouped affected metrics: " + incident.affectedMetrics() + ".");
        }
        if (profile != null && hasText(profile.baselineSummary())) {
            factors.add(profile.baselineSummary());
        }

        if (factors.isEmpty()) {
            factors.add("No strong secondary contributing factor is confirmed yet; verify process metrics and recent workload changes.");
        }
        return factors;
    }

    private List<String> buildRecommendedChecks(
            String metric,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            ProcessMetricResponse topCpuProcess,
            ProcessMetricResponse topMemoryProcess
    ) {
        List<String> checks = new ArrayList<>();
        if ("CPU".equals(metric)) {
            checks.add("Check the process table and confirm whether CPU has shifted to " + processOrFallback(topCpuProcess) + ".");
            checks.addAll(operatorAdvice(topCpuProcess, "Review recent user activity, builds, browser tabs, or container workloads around the spike."));
        } else if ("MEMORY".equals(metric)) {
            checks.add("Check the top memory-consuming processes and compare them with " + processOrFallback(topMemoryProcess) + ".");
            checks.addAll(operatorAdvice(topMemoryProcess, "Review swap activity, JVM memory metrics, and container memory usage if available."));
        } else if ("DISK".equals(metric)) {
            checks.add("Check disk usage by directory and identify the largest log, cache, Docker, and build-artifact paths.");
            checks.add("Review Docker system usage and persistent database volume growth before deleting anything.");
        }

        if (!context.activeAlerts().isEmpty()) {
            checks.add("Compare this issue with the other active alerts on the same machine to see whether it is isolated or part of broader resource pressure.");
        }
        if (context.recentAnomalies() != null && !context.recentAnomalies().isEmpty()) {
            checks.add("Compare the anomaly timestamps with the alert window to confirm whether the abnormal behavior is still ongoing.");
        }
        return checks;
    }

    private List<String> buildRecommendedActions(
            String metric,
            String trendAssessment,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            ProcessMetricResponse topCpuProcess,
            ProcessMetricResponse topMemoryProcess
    ) {
        List<String> actions = new ArrayList<>();
        if ("CPU".equals(metric)) {
            actions.add("If " + processOrFallback(topCpuProcess) + " still dominates CPU, reduce the workload, close heavy tabs or tools, or pause the runaway task.");
            actions.add("If the spike already recovered, keep monitoring for repeat spikes before taking disruptive action.");
        } else if ("MEMORY".equals(metric)) {
            actions.add("Close or restart the heaviest memory consumers if pressure remains high, especially " + processOrFallback(topMemoryProcess) + ".");
            actions.add("If memory keeps climbing, capture heap or container memory evidence and plan a controlled restart rather than repeated force-kills.");
        } else if ("DISK".equals(metric)) {
            actions.add("Rotate logs, remove stale artifacts, and clean Docker images or volumes carefully after confirming they are safe to remove.");
            actions.add("If disk usage keeps rising after cleanup, plan additional capacity or move the largest persistent data elsewhere.");
        }

        if (trendAssessment.toLowerCase(Locale.ROOT).contains("persistent")
                || trendAssessment.toLowerCase(Locale.ROOT).contains("worsening")) {
            actions.add("Escalate if the metric remains above threshold across the next few telemetry samples.");
        }
        return actions;
    }

    private List<String> buildMonitorNext(
            String metric,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            String trendAssessment
    ) {
        List<String> monitor = new ArrayList<>();
        monitor.add("Watch the next 3 to 5 telemetry samples for " + metric.toLowerCase(Locale.ROOT) + " to confirm whether the trend is recovering or repeating.");
        if (!context.activeAlerts().isEmpty()) {
            monitor.add("Monitor whether related alerts on the same machine clear together or diverge.");
        }
        if ("DISK".equals(metric)) {
            monitor.add("Track free disk headroom and the growth rate of the largest directories.");
        } else if ("MEMORY".equals(metric)) {
            monitor.add("Track whether memory returns to a lower baseline or continues climbing over time.");
        } else if ("CPU".equals(metric)) {
            monitor.add("Track whether the same process becomes the top CPU contributor again during future spikes.");
        }
        if (trendAssessment.toLowerCase(Locale.ROOT).contains("transient")) {
            monitor.add("If the issue does not recur, treat this as a transient event rather than immediate sustained degradation.");
        }
        return monitor;
    }

    private List<String> buildTimelineSummary(List<CorrelationTimelineEntry> correlationTimeline) {
        if (correlationTimeline == null || correlationTimeline.isEmpty()) {
            return List.of("No correlated timeline events were available for this incident window.");
        }

        return correlationTimeline.stream()
                .map(entry -> {
                    String timestamp = entry.getTimestamp() != null
                            ? entry.getTimestamp().toLocalTime().withNano(0).toString()
                            : "unknown";
                    return timestamp + " — " + normalize(entry.getType()).replace('_', ' ') + " — " + entry.getDescription();
                })
                .toList();
    }

    private String buildUrgency(String severity, String trendAssessment, List<AlertResponse> activeAlerts) {
        if ("CRITICAL".equals(severity)) {
            return "Critical: this needs attention soon because the alert severity is critical.";
        }
        if ("HIGH".equals(severity) && trendAssessment.toLowerCase(Locale.ROOT).contains("persistent")) {
            return "Warning: the alert is high severity and the pressure still appears persistent.";
        }
        if ("HIGH".equals(severity)) {
            return "Warning: the alert is high severity, but the latest trend suggests it may be recovering.";
        }
        if (activeAlerts.size() > 1) {
            return "Warning: multiple active alerts suggest broader system pressure.";
        }
        return "Normal: keep an eye on it, but no disruptive action is recommended without confirming the trend.";
    }

    private String assessTrend(
            String metric,
            Double alertValue,
            TelemetrySnapshotDetailResponse latestTelemetry,
            List<TelemetrySnapshotDetailResponse> telemetryHistory
    ) {
        Double current = currentMetricValue(metric, latestTelemetry);
        Double oldest = telemetryHistory != null && !telemetryHistory.isEmpty()
                ? currentMetricValue(metric, telemetryHistory.get(telemetryHistory.size() - 1))
                : null;

        if (alertValue == null && current == null) {
            return "Trend assessment is limited because recent metric history is unavailable.";
        }
        if (alertValue != null && current != null) {
            double delta = current - alertValue;
            if (delta <= -10.0) {
                return String.format("%s has dropped from %.1f%% to %.1f%%, so this appears more transient or already recovering.", title(metric), alertValue, current);
            }
            if (delta >= 5.0) {
                return String.format("%s has risen from %.1f%% to %.1f%%, so pressure appears persistent or worsening.", title(metric), alertValue, current);
            }
        }
        if (current != null && oldest != null) {
            double trendDelta = current - oldest;
            if (trendDelta >= 5.0) {
                return String.format("%s has increased across recent telemetry and still appears persistent.", title(metric));
            }
            if (trendDelta <= -5.0) {
                return String.format("%s has eased across recent telemetry and appears to be recovering.", title(metric));
            }
        }
        return String.format("%s remains near the recent level, so verify the next few samples before assuming it is transient.", title(metric));
    }

    private Double currentMetricValue(String metric, TelemetrySnapshotDetailResponse telemetry) {
        if (telemetry == null) {
            return null;
        }
        return switch (metric) {
            case "CPU" -> telemetry.getCpuUsage();
            case "MEMORY" -> telemetry.getMemoryUsage();
            case "DISK" -> telemetry.getDiskUsage();
            default -> null;
        };
    }

    private String processOrFallback(ProcessMetricResponse processMetricResponse) {
        if (processMetricResponse == null || !hasText(processMetricResponse.getProcessName())) {
            return "the current top resource-consuming process";
        }
        ProcessInsight insight = processInsight(processMetricResponse);
        if (insight != null && hasText(insight.category())) {
            return insight.category().toLowerCase(Locale.ROOT) + " workload";
        }
        return processMetricResponse.getProcessName();
    }

    private ProcessInsight processInsight(ProcessMetricResponse processMetricResponse) {
        if (processMetricResponse == null || !hasText(processMetricResponse.getProcessName())) {
            return null;
        }
        return processEnrichmentService.enrich(processMetricResponse.getProcessName()).orElse(null);
    }

    private String processEvidenceLabel(ProcessMetricResponse processMetricResponse) {
        ProcessInsight insight = processInsight(processMetricResponse);
        if (insight == null || !hasText(insight.category())) {
            return processMetricResponse.getProcessName();
        }
        return insight.category() + " (" + processMetricResponse.getProcessName() + ")";
    }

    private List<String> operatorAdvice(ProcessMetricResponse processMetricResponse, String fallback) {
        ProcessInsight insight = processInsight(processMetricResponse);
        if (insight == null || insight.operatorAdvice() == null || insight.operatorAdvice().isEmpty()) {
            return List.of(fallback);
        }
        return insight.operatorAdvice();
    }

    private String joinPlain(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "the active workload";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return String.join(", ", values.subList(0, values.size() - 1)) + ", or " + values.get(values.size() - 1);
    }

    private String plainCause(String likelyCause) {
        if (!hasText(likelyCause)) {
            return "a workload is using more resources than usual";
        }
        String cause = likelyCause.trim();
        if (cause.endsWith(".")) {
            cause = cause.substring(0, cause.length() - 1);
        }
        return cause;
    }

    private String defaultWhy(String metric) {
        return switch (metric) {
            case "CPU" -> "apps are building, indexing, refreshing data, or doing background work";
            case "MEMORY" -> "large apps, browser tabs, containers, or long-running services stay open for a while";
            case "DISK" -> "logs, caches, Docker images, database files, or build artifacts grow over time";
            default -> "a local workload is busier than normal";
        };
    }

    private String seriousnessSentence(
            String metric,
            String severity,
            String trendAssessment,
            MachineBehaviorProfileService.MachineBehaviorProfile profile,
            IncidentCorrelationService.IncidentCorrelationResult incident,
            RootCauseConfidenceService.ConfidenceScore confidenceScore
    ) {
        String trend = trendAssessment != null ? trendAssessment.toLowerCase(Locale.ROOT) : "";
        if ("CRITICAL".equals(severity)) {
            return "This is critical and should be checked now.";
        }
        if (trend.contains("persistent") || trend.contains("worsening")) {
            return "This is above the recent baseline and may indicate abnormal machine behavior.";
        }
        if (incident != null && incident.relatedInvestigationCount() > 0) {
            return "This has appeared in related investigations, so it is worth watching closely.";
        }
        if (confidenceScore != null && confidenceScore.score() >= 70 && hasBaselineSignal(profile)) {
            return "This behavior is unusual compared with this machine's recent baseline.";
        }
        if (isDeveloperWorkload(metric, profile)) {
            return "This workload appears normal for an active local development environment.";
        }
        if ("HIGH".equals(severity)) {
            return "This is a warning-level issue, but it does not currently look critical.";
        }
        return "The system is under moderate load but does not currently appear critical.";
    }

    private boolean hasBaselineSignal(MachineBehaviorProfileService.MachineBehaviorProfile profile) {
        return profile != null
                && ((hasText(profile.baselineSummary()) && !profile.baselineSummary().toLowerCase(Locale.ROOT).contains("baseline unavailable"))
                || hasText(profile.historicalPatternNotes()));
    }

    private boolean isDeveloperWorkload(String metric, MachineBehaviorProfileService.MachineBehaviorProfile profile) {
        if ("DISK".equals(metric)) {
            return false;
        }
        String notes = profile != null && hasText(profile.historicalPatternNotes())
                ? profile.historicalPatternNotes().toLowerCase(Locale.ROOT)
                : "";
        return notes.contains("development") || notes.contains("recurring workload") || notes.contains("top process");
    }

    private List<String> defaultChecks(String metric) {
        return switch (metric) {
            case "CPU" -> List.of("Check the busiest app or process.", "Close unused tabs or tools.", "Monitor CPU over the next few minutes.");
            case "MEMORY" -> List.of("Check the top memory consumers.", "Close unused apps.", "Monitor memory over the next few telemetry samples.");
            case "DISK" -> List.of("Check the largest folders.", "Clean stale logs or build artifacts carefully.", "Confirm free space recovers afterward.");
            default -> List.of("Check the latest telemetry.", "Look for recent workload changes.", "Monitor the next few samples.");
        };
    }

    private String joinBullets(List<String> lines) {
        return lines.stream()
                .filter(this::hasText)
                .distinct()
                .map(line -> "- " + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- No additional details available.");
    }

    private String normalize(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String title(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "Resource" : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String formatPercent(Double value) {
        return value == null ? "unknown" : String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String formatPercent(Double value, String suffix) {
        return value == null ? "" : " at " + String.format(Locale.ROOT, "%.1f", value) + "%" + suffix;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record InvestigationTriage(
            String summary,
            String likelyCause,
            String evidence,
            String contributingFactors,
            String recommendedChecks,
            String recommendedActions,
            String urgencyAssessment,
            String persistenceAssessment,
            String monitorNext,
            String correlationTimeline
    ) {
    }
}
