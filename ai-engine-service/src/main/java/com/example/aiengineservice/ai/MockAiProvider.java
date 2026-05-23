package com.example.aiengineservice.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MockAiProvider implements AiProvider {

    @Override
    public String generateInsight(AiInsightRequest request) {
        double cpuUsage = request.getCpuUsage() != null ? request.getCpuUsage() : 0.0;
        double memoryUsage = request.getMemoryUsage() != null ? request.getMemoryUsage() : 0.0;
        double diskUsage = request.getDiskUsage() != null ? request.getDiskUsage() : 0.0;
        int activeAlertCount = request.getActiveAlerts() != null && request.getActiveAlerts().getCount() != null
                ? request.getActiveAlerts().getCount()
                : 0;
        int anomalyCount = request.getAnomalies() != null && request.getAnomalies().getCount() != null
                ? request.getAnomalies().getCount()
                : 0;
        AiInsightRequest.TopProcessSummary topProcess = request.getTopProcess();

        if (hasFocusEvent(request)) {
            return buildFocusedEventInsight(request, topProcess, activeAlertCount, anomalyCount);
        }

        List<String> recommendations = new ArrayList<>();
        String happening = "System behavior is currently within expected operating bounds.";

        if (activeAlertCount > 0) {
            happening = "There are active alerts that suggest the system needs attention.";
            recommendations.add("Review the active alerts first because they indicate the highest-priority issues.");
        } else if (anomalyCount > 0) {
            happening = "Recent anomaly signals suggest this machine is behaving differently than usual.";
            recommendations.add("Compare the latest anomaly signals against the current telemetry trend to confirm whether the issue is still active.");
        } else if (cpuUsage > 85.0) {
            happening = "CPU usage is elevated and a busy workload may be affecting responsiveness.";
        } else if (memoryUsage > 80.0) {
            happening = "Memory usage is high and the machine may slow down if it keeps rising.";
        } else if (diskUsage > 85.0) {
            happening = "Disk usage is getting high and free space is becoming limited.";
        }

        if (memoryUsage > 80.0) {
            recommendations.add("Close or restart heavy applications to reduce memory pressure.");
        }

        if (diskUsage > 85.0) {
            recommendations.add("Clean large files, downloads, or old logs to recover disk space.");
        }

        if (topProcess != null && topProcess.getName() != null && !topProcess.getName().isBlank()) {
            if (topProcess.getCpu() != null && topProcess.getCpu() > 20.0) {
                recommendations.add(adviceOrDefault(topProcess, "Inspect this workload because it is one of the highest CPU consumers."));
            }

            if (topProcess.getMemory() != null && topProcess.getMemory() > 15.0) {
                recommendations.add(adviceOrDefault(topProcess, "Review this workload for memory growth or unnecessary background work."));
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Continue monitoring the system because no urgent action is required right now.");
        }

        String processMeaning = null;
        if (topProcess != null && topProcess.getName() != null && !topProcess.getName().isBlank()) {
            if (hasProcessInsight(topProcess)) {
                processMeaning = topProcess.getHumanExplanation();
            } else {
                processMeaning = "The busiest process is currently using more resources than the others.";
            }
        }

        return formatStructuredResponse(
                processMeaning != null ? processMeaning : happening,
                whyThisHappens(topProcess),
                seriousness(request, activeAlertCount, anomalyCount),
                recommendations
        );
    }

    private boolean hasFocusEvent(AiInsightRequest request) {
        return request.getFocusMetric() != null && !request.getFocusMetric().isBlank()
                && request.getFocusTimestamp() != null && !request.getFocusTimestamp().isBlank()
                && request.getFocusValue() != null;
    }

    private String buildFocusedEventInsight(
            AiInsightRequest request,
            AiInsightRequest.TopProcessSummary topProcess,
            int activeAlertCount,
            int anomalyCount
    ) {
        String metric = request.getFocusMetric().toUpperCase();
        String source = request.getFocusSource() != null ? request.getFocusSource() : "telemetry";
        double value = request.getFocusValue();

        String happening;
        List<String> actions = new ArrayList<>();

        if (topProcess != null && topProcess.getName() != null && !topProcess.getName().isBlank()) {
            if (hasProcessInsight(topProcess)) {
                happening = topProcess.getHumanExplanation();
            } else {
                happening = metric + " is elevated and the busiest workload is a likely contributor.";
            }
            actions.add(adviceOrDefault(topProcess, "Inspect the busiest workload for runaway work, heavy tabs or tools, retries, or sustained resource usage."));
        } else {
            happening = metric + " reached " + String.format("%.1f", value) + "% and the top contributor still needs to be confirmed.";
        }

        if (activeAlertCount > 0) {
            actions.add("Review the active alerts around this time window to confirm whether the spike triggered or worsened them.");
        }

        if ("MEMORY".equals(metric) || memoryUsageHigh(request)) {
            actions.add("Check top memory consumers, JVM or container memory pressure, and close or restart heavy applications if usage remains elevated.");
        }

        if ("DISK".equals(metric) || diskUsageHigh(request)) {
            actions.add("Check log growth, Docker storage, database files, and remove stale artifacts carefully if free space stays low.");
        }

        if ("CPU".equals(metric)) {
            actions.add("Check for runaway scripts, browser tabs, builds, container work, or unexpected compute bursts.");
        }

        if (actions.isEmpty()) {
            actions.add("Monitor the next few telemetry points to see whether this spike was transient or sustained.");
        }

        return formatStructuredResponse(
                "From " + source + ": " + happening,
                whyThisHappens(topProcess),
                seriousness(request, activeAlertCount, anomalyCount),
                actions
        );
    }

    private boolean memoryUsageHigh(AiInsightRequest request) {
        return request.getMemoryUsage() != null && request.getMemoryUsage() > 80.0;
    }

    private boolean diskUsageHigh(AiInsightRequest request) {
        return request.getDiskUsage() != null && request.getDiskUsage() > 85.0;
    }

    @Override
    public String generateChatResponse(String userMessage, AiInsightRequest context) {
        String normalizedMessage = userMessage == null ? "" : userMessage.trim().toLowerCase();
        String baseInsight = generateInsight(context);
        AiInsightRequest.TopProcessSummary topProcess = context.getTopProcess();

        if (normalizedMessage.contains("cpu")) {
            return formatStructuredResponse(
                    "CPU is likely elevated because the current workload is using more compute than usual.",
                    whyThisHappens(topProcess),
                    seriousness(context, context.getActiveAlerts() != null ? context.getActiveAlerts().getCount() : 0,
                            context.getAnomalies() != null ? context.getAnomalies().getCount() : 0),
                    List.of("Check whether CPU is recovering or repeating.", "Inspect the busiest workload.", "Review any active CPU alerts.")
            );
        }

        if (normalizedMessage.contains("memory")) {
            return formatStructuredResponse(
                    "Memory pressure usually comes from heavy apps or long-running background work.",
                    "This often happens when browser tabs, containers, IDEs, or services stay open for a while.",
                    seriousness(context, context.getActiveAlerts() != null ? context.getActiveAlerts().getCount() : 0,
                            context.getAnomalies() != null ? context.getAnomalies().getCount() : 0),
                    List.of("Review the top memory consumers.", "Close unused apps.", "Watch whether memory drops over the next few telemetry updates.")
            );
        }

        if (normalizedMessage.contains("fix first") || normalizedMessage.contains("first")) {
            return "Fix the highest-severity active alert first, then confirm whether recent anomalies clear afterward. "
                    + "If there are no active alerts, start with the process contributing the most resource usage.";
        }

        if (normalizedMessage.contains("anomaly")) {
            return formatStructuredResponse(
                    "The latest anomaly suggests this machine moved outside its normal pattern.",
                    whyThisHappens(topProcess),
                    "This may be abnormal machine behavior, especially if it repeats or keeps rising.",
                    List.of("Compare the anomaly timing with active alerts.", "Check recent process activity.", "Monitor the next few samples.")
            );
        }

        return "Here is the current system summary: " + baseInsight;
    }

    private boolean hasProcessInsight(AiInsightRequest.TopProcessSummary process) {
        return process != null
                && process.getHumanExplanation() != null
                && !process.getHumanExplanation().isBlank();
    }

    private String adviceOrDefault(AiInsightRequest.TopProcessSummary process, String fallback) {
        if (process != null && process.getOperatorAdvice() != null && !process.getOperatorAdvice().isEmpty()) {
            return process.getOperatorAdvice().get(0);
        }
        return fallback;
    }

    private String whyThisHappens(AiInsightRequest.TopProcessSummary process) {
        if (process != null && process.getLikelyCauses() != null && !process.getLikelyCauses().isEmpty()) {
            return "This usually happens when " + joinPlain(process.getLikelyCauses()) + ".";
        }
        return "This usually happens when local apps, builds, browser tabs, or background services are busy.";
    }

    private String seriousness(AiInsightRequest request, int activeAlertCount, int anomalyCount) {
        if (request.getConfidenceLevel() != null && "CRITICAL".equalsIgnoreCase(request.getConfidenceLevel())) {
            return "This looks critical and should be checked now.";
        }
        if (activeAlertCount > 0 || anomalyCount > 0) {
            return "This may be abnormal machine behavior, so it is worth checking.";
        }
        if (request.getHistoricalPatternNotes() != null
                && request.getHistoricalPatternNotes().toLowerCase().contains("recurring workload")) {
            return "This workload appears normal for an active local development environment.";
        }
        return "The system does not currently appear overloaded.";
    }

    private String formatStructuredResponse(String happening, String why, String serious, List<String> actions) {
        List<String> nextSteps = actions == null || actions.isEmpty()
                ? List.of("Monitor the next few telemetry samples.")
                : actions.stream().distinct().limit(4).toList();
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
                """.formatted(happening, why, serious, joinBullets(nextSteps)).trim();
    }

    private String joinPlain(List<String> values) {
        if (values.size() == 1) {
            return values.get(0);
        }
        return String.join(", ", values.subList(0, values.size() - 1)) + ", or " + values.get(values.size() - 1);
    }

    private String joinBullets(List<String> values) {
        return values.stream()
                .map(value -> "- " + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- Monitor the next few telemetry samples.");
    }
}
