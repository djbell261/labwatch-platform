package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MachineBehaviorProfileService {

    public MachineBehaviorProfile build(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context
    ) {
        List<TelemetrySnapshotDetailResponse> telemetry = context.recentTelemetry() == null
                ? List.of()
                : context.recentTelemetry();
        double avgCpu = average(telemetry.stream().map(TelemetrySnapshotDetailResponse::getCpuUsage).toList());
        double avgMemory = average(telemetry.stream().map(TelemetrySnapshotDetailResponse::getMemoryUsage).toList());
        double avgDisk = average(telemetry.stream().map(TelemetrySnapshotDetailResponse::getDiskUsage).toList());
        double cpuStdDev = stdDev(telemetry.stream().map(TelemetrySnapshotDetailResponse::getCpuUsage).toList(), avgCpu);
        double memoryStdDev = stdDev(telemetry.stream().map(TelemetrySnapshotDetailResponse::getMemoryUsage).toList(), avgMemory);
        String commonProcess = commonTopProcess(telemetry);
        boolean repeatedTopProcess = hasText(commonProcess)
                && sameProcess(commonProcess, dominantProcessName(alertEventMessage, context));
        boolean unusualForMachine = isUnusual(alertEventMessage, avgCpu, avgMemory, avgDisk, cpuStdDev, memoryStdDev);
        String activeHours = summarizeActiveHours(telemetry);

        return new MachineBehaviorProfile(
                avgCpu,
                avgMemory,
                avgDisk,
                cpuStdDev,
                memoryStdDev,
                commonProcess,
                repeatedTopProcess,
                unusualForMachine,
                activeHours,
                buildBaselineSummary(alertEventMessage, avgCpu, avgMemory, avgDisk, cpuStdDev, memoryStdDev, commonProcess, activeHours),
                buildHistoricalPatternNotes(alertEventMessage, context, commonProcess, repeatedTopProcess, unusualForMachine)
        );
    }

    private String buildBaselineSummary(
            AlertEventMessage alertEventMessage,
            double avgCpu,
            double avgMemory,
            double avgDisk,
            double cpuStdDev,
            double memoryStdDev,
            String commonProcess,
            String activeHours
    ) {
        String metric = normalize(alertEventMessage != null ? alertEventMessage.getAlertType() : null);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(
                Locale.ROOT,
                "Recent baseline for this machine is CPU %.1f%% (std dev %.1f), memory %.1f%% (std dev %.1f), and disk %.1f%%.",
                avgCpu,
                cpuStdDev,
                avgMemory,
                memoryStdDev,
                avgDisk
        ));
        if (hasText(commonProcess)) {
            builder.append(" Common top process: ").append(commonProcess).append(".");
        }
        if (hasText(activeHours)) {
            builder.append(" Typical observed activity window: ").append(activeHours).append(".");
        }
        if ("CPU".equals(metric) && alertEventMessage != null && alertEventMessage.getMetricValue() != null) {
            builder.append(alertEventMessage.getMetricValue() > avgCpu + Math.max(15.0, cpuStdDev * 2)
                    ? " Current CPU is unusual for this recent baseline."
                    : " Current CPU is closer to recent machine behavior.");
        }
        if ("MEMORY".equals(metric) && alertEventMessage != null && alertEventMessage.getMetricValue() != null) {
            builder.append(alertEventMessage.getMetricValue() > avgMemory + Math.max(10.0, memoryStdDev * 2)
                    ? " Current memory is unusual for this recent baseline."
                    : " Current memory is closer to recent machine behavior.");
        }
        return builder.toString();
    }

    private String buildHistoricalPatternNotes(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context,
            String commonProcess,
            boolean repeatedTopProcess,
            boolean unusualForMachine
    ) {
        StringBuilder builder = new StringBuilder();
        if (repeatedTopProcess && hasText(commonProcess)) {
            builder.append(commonProcess)
                    .append(" appears repeatedly as a top process, so this may be a recurring workload pattern rather than a one-off signal.");
        } else {
            builder.append("No single recurring top process is strong enough to treat as normal without operator verification.");
        }
        if (context.recentAnomalies() != null && !context.recentAnomalies().isEmpty()) {
            builder.append(" Recent anomaly history increases suspicion that the pattern is operationally meaningful.");
        }
        if (unusualForMachine) {
            builder.append(" The current reading is outside this machine's recent behavior and should be treated as more notable.");
        } else {
            builder.append(" The current reading is not far outside the recent rolling baseline.");
        }
        String metric = normalize(alertEventMessage != null ? alertEventMessage.getAlertType() : null);
        if ("CPU".equals(metric) && repeatedTopProcess && isDevTool(commonProcess)) {
            builder.append(" Developer tooling is a plausible benign contributor when builds, tests, or indexing are active.");
        }
        return builder.toString();
    }

    private boolean isUnusual(AlertEventMessage alertEventMessage, double avgCpu, double avgMemory, double avgDisk, double cpuStdDev, double memoryStdDev) {
        if (alertEventMessage == null || alertEventMessage.getMetricValue() == null) {
            return false;
        }
        String metric = normalize(alertEventMessage.getAlertType());
        double value = alertEventMessage.getMetricValue();
        return switch (metric) {
            case "CPU" -> value > avgCpu + Math.max(15.0, cpuStdDev * 2);
            case "MEMORY" -> value > avgMemory + Math.max(10.0, memoryStdDev * 2);
            case "DISK" -> value > avgDisk + 8.0;
            default -> false;
        };
    }

    private String dominantProcessName(AlertEventMessage alertEventMessage, AiInvestigationContextBuilder.AiInvestigationContext context) {
        String metric = normalize(alertEventMessage != null ? alertEventMessage.getAlertType() : null);
        ProcessMetricResponse process = "MEMORY".equals(metric) ? context.topMemoryProcess() : context.topCpuProcess();
        return process != null ? process.getProcessName() : null;
    }

    private String commonTopProcess(List<TelemetrySnapshotDetailResponse> telemetry) {
        return telemetry.stream()
                .map(snapshot -> topProcess(snapshot.getProcessMetrics()))
                .filter(Objects::nonNull)
                .map(ProcessMetricResponse::getProcessName)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(this::processKey, Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private ProcessMetricResponse topProcess(List<ProcessMetricResponse> processMetrics) {
        if (processMetrics == null || processMetrics.isEmpty()) {
            return null;
        }
        return processMetrics.stream()
                .max(Comparator.comparing(
                        item -> Math.max(
                                item.getCpuPercent() != null ? item.getCpuPercent() : 0.0,
                                item.getMemoryPercent() != null ? item.getMemoryPercent() : 0.0
                        )
                ))
                .orElse(null);
    }

    private String summarizeActiveHours(List<TelemetrySnapshotDetailResponse> telemetry) {
        List<Integer> hours = telemetry.stream()
                .map(TelemetrySnapshotDetailResponse::getTimestamp)
                .filter(Objects::nonNull)
                .map(timestamp -> timestamp.toLocalTime().getHour())
                .sorted()
                .toList();
        if (hours.isEmpty()) {
            return "unknown";
        }
        return LocalTime.of(hours.get(0), 0) + "-" + LocalTime.of(hours.get(hours.size() - 1), 0);
    }

    private double average(List<Double> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double stdDev(List<Double> values, double average) {
        List<Double> present = values.stream().filter(Objects::nonNull).toList();
        if (present.size() < 2) {
            return 0.0;
        }
        double variance = present.stream()
                .mapToDouble(value -> Math.pow(value - average, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    private boolean sameProcess(String left, String right) {
        return hasText(left) && hasText(right) && processKey(left).equals(processKey(right));
    }

    private boolean isDevTool(String processName) {
        String key = processKey(processName);
        return key.contains("java") || key.contains("gradle") || key.contains("intellij") || key.contains("idea");
    }

    private String processKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record MachineBehaviorProfile(
            double averageCpu,
            double averageMemory,
            double averageDisk,
            double cpuStdDev,
            double memoryStdDev,
            String commonTopProcess,
            boolean repeatedTopProcess,
            boolean unusualForMachine,
            String typicalActiveHours,
            String baselineSummary,
            String historicalPatternNotes
    ) {
    }
}
