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

        AiInsightRequest.TopProcessSummary topProcess = request.getTopProcess();
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
}
