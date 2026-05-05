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
        String likelyIssue = "System behavior is currently within expected operating bounds.";

        if (activeAlertCount > 0) {
            likelyIssue = "There are active alerts that suggest the system is under immediate pressure.";
            recommendations.add("Review the active alerts first because they indicate the highest-priority issues.");
        } else if (anomalyCount > 0) {
            likelyIssue = "Recent anomaly signals suggest system behavior has drifted from the normal baseline.";
            recommendations.add("Compare the latest anomaly signals against the current telemetry trend to confirm whether the issue is still active.");
        } else if (cpuUsage > 85.0) {
            likelyIssue = "CPU usage is elevated and compute-intensive work is likely affecting system responsiveness.";
        } else if (memoryUsage > 80.0) {
            likelyIssue = "Memory usage is high and the machine may begin slowing down if pressure continues.";
        } else if (diskUsage > 85.0) {
            likelyIssue = "Disk usage is nearing capacity and storage headroom is becoming limited.";
        }

        if (memoryUsage > 80.0) {
            recommendations.add("Close or restart heavy applications to reduce memory pressure.");
        }

        if (diskUsage > 85.0) {
            recommendations.add("Clean large files, downloads, or old logs to recover disk space.");
        }

        if (topProcess != null && topProcess.getName() != null && !topProcess.getName().isBlank()) {
            if (topProcess.getCpu() != null && topProcess.getCpu() > 20.0) {
                recommendations.add("Inspect process " + topProcess.getName() + " because it is one of the highest CPU consumers.");
            }

            if (topProcess.getMemory() != null && topProcess.getMemory() > 15.0) {
                recommendations.add("Review process " + topProcess.getName() + " for memory growth or unnecessary background work.");
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Continue monitoring the system because no urgent action is required right now.");
        }

        StringBuilder response = new StringBuilder();
        response.append(likelyIssue);

        if (topProcess != null && topProcess.getName() != null && !topProcess.getName().isBlank()) {
            response.append(" ")
                    .append(topProcess.getName())
                    .append(" is currently one of the most active processes");

            if (topProcess.getCpu() != null) {
                response.append(" at ").append(String.format("%.1f", topProcess.getCpu())).append("% CPU");
            }

            if (topProcess.getMemory() != null) {
                response.append(" and ").append(String.format("%.1f", topProcess.getMemory())).append("% memory");
            }

            response.append(".");
        }

        response.append(" Recommended actions: ")
                .append(String.join(" ", recommendations));

        return response.toString();
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

        List<String> likelyCauses = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        likelyCauses.add(metric + " spiked to " + String.format("%.1f", value) + "% at " + request.getFocusTimestamp()
                + ", which can indicate short-term system pressure or a workload surge.");

        if (topProcess != null && topProcess.getName() != null && !topProcess.getName().isBlank()) {
            likelyCauses.add(topProcess.getName() + " is one of the strongest contributing processes right now"
                    + (topProcess.getCpu() != null ? " at " + String.format("%.1f", topProcess.getCpu()) + "% CPU" : "")
                    + ".");
            actions.add("Inspect process " + topProcess.getName() + " for runaway work, retries, or heavy resource usage.");
        }

        if (activeAlertCount > 0) {
            likelyCauses.add("There are " + activeAlertCount + " active alerts, so this spike likely aligns with a broader health issue.");
            actions.add("Review the active alerts around this time window to confirm whether the spike triggered or worsened them.");
        }

        if (anomalyCount > 0) {
            likelyCauses.add("Recent anomaly detection also flagged unusual behavior, which suggests this was outside the normal baseline.");
        }

        if ("MEMORY".equals(metric) || memoryUsageHigh(request)) {
            actions.add("Close or restart heavy applications if memory pressure remains elevated.");
        }

        if ("DISK".equals(metric) || diskUsageHigh(request)) {
            actions.add("Free disk space by removing stale logs, downloads, or unused large files.");
        }

        if ("CPU".equals(metric)) {
            actions.add("Check for runaway scripts, background jobs, or unexpected compute bursts.");
        }

        if (actions.isEmpty()) {
            actions.add("Monitor the next few telemetry points to see whether this spike was transient or sustained.");
        }

        return "Focused event from " + source + ": " + String.join(" ", likelyCauses)
                + " Recommended action: " + String.join(" ", actions);
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
            return "CPU is likely elevated because current workload is consuming more compute than usual. "
                    + (topProcess != null && topProcess.getName() != null && !topProcess.getName().isBlank()
                    ? topProcess.getName() + " is one of the main CPU consumers right now. "
                    : "")
                    + "Start by inspecting the busiest process and any active alerts tied to CPU pressure.";
        }

        if (normalizedMessage.contains("memory")) {
            return "Memory pressure usually comes from heavy applications or long-running background tasks. "
                    + "Close unused apps, restart large processes, and watch whether memory drops over the next few telemetry updates.";
        }

        if (normalizedMessage.contains("fix first") || normalizedMessage.contains("first")) {
            return "Fix the highest-severity active alert first, then confirm whether recent anomalies clear afterward. "
                    + "If there are no active alerts, start with the process contributing the most resource usage.";
        }

        if (normalizedMessage.contains("anomaly")) {
            return "The latest anomaly suggests behavior moved outside the normal baseline for that metric. "
                    + (topProcess != null && topProcess.getName() != null && !topProcess.getName().isBlank()
                    ? "A likely contributor is " + topProcess.getName() + ", which is currently a top resource consumer. "
                    : "")
                    + "Compare the anomaly timing with alerts and recent process activity to confirm the root cause.";
        }

        return "Here is the current system summary: " + baseInsight;
    }
}
