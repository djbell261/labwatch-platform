package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.CorrelationTimelineEntry;
import com.example.aiengineservice.dto.external.AlertResponse;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import com.example.aiengineservice.entity.Anomaly;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AiSignalCorrelationService {

    private static final ParameterizedTypeReference<List<TelemetrySnapshotDetailResponse>> TELEMETRY_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<AlertResponse>> ALERT_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient monitoringApiClient;
    private final RestClient alertEngineClient;
    private final AnomalyQueryService anomalyQueryService;
    private final int lookbackMinutes;
    private final int lookaheadMinutes;
    private final int maxEvents;

    public AiSignalCorrelationService(
            AnomalyQueryService anomalyQueryService,
            @Value("${services.monitoring-api.base-url:http://monitoring-api:8089}") String monitoringApiBaseUrl,
            @Value("${services.alert-engine.base-url:http://alert-engine:8088}") String alertEngineBaseUrl,
            @Value("${services.http.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${services.http.read-timeout-ms:3000}") int readTimeoutMs,
            @Value("${labwatch.ai.correlation.lookback-minutes:10}") int lookbackMinutes,
            @Value("${labwatch.ai.correlation.lookahead-minutes:5}") int lookaheadMinutes,
            @Value("${labwatch.ai.correlation.max-events:12}") int maxEvents
    ) {
        this.anomalyQueryService = anomalyQueryService;
        this.monitoringApiClient = RestClient.builder()
                .baseUrl(monitoringApiBaseUrl)
                .requestFactory(buildRequestFactory(connectTimeoutMs, readTimeoutMs))
                .build();
        this.alertEngineClient = RestClient.builder()
                .baseUrl(alertEngineBaseUrl)
                .requestFactory(buildRequestFactory(connectTimeoutMs, readTimeoutMs))
                .build();
        this.lookbackMinutes = lookbackMinutes;
        this.lookaheadMinutes = lookaheadMinutes;
        this.maxEvents = maxEvents;
    }

    public List<CorrelationTimelineEntry> buildTimeline(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context
    ) {
        if (alertEventMessage == null || alertEventMessage.getCreatedAt() == null) {
            return List.of();
        }

        String machineIdentifier = alertEventMessage.getMachineIdentifier();
        if (machineIdentifier == null || machineIdentifier.isBlank()) {
            return List.of();
        }
        LocalDateTime anchor = alertEventMessage.getCreatedAt();
        LocalDateTime windowStart = anchor.minusMinutes(lookbackMinutes);
        LocalDateTime windowEnd = anchor.plusMinutes(lookaheadMinutes);

        List<TelemetrySnapshotDetailResponse> telemetry = fetchTelemetry(machineIdentifier).stream()
                .filter(item -> withinWindow(item.getTimestamp(), windowStart, windowEnd))
                .sorted(Comparator.comparing(TelemetrySnapshotDetailResponse::getTimestamp))
                .toList();
        List<AlertResponse> alerts = fetchAlerts(machineIdentifier).stream()
                .filter(item -> withinWindow(item.getCreatedAt(), windowStart, windowEnd)
                        || withinWindow(item.getResolvedAt(), windowStart, windowEnd))
                .toList();
        List<Anomaly> anomalies = anomalyQueryService.findByMachineIdentifier(machineIdentifier).stream()
                .filter(item -> withinWindow(item.getDetectedAt(), windowStart, windowEnd))
                .toList();

        List<CorrelationTimelineEntry> events = new ArrayList<>();
        addTelemetryEvents(events, alertEventMessage, telemetry, context);
        addAlertEvents(events, machineIdentifier, alerts);
        addAnomalyEvents(events, machineIdentifier, anomalies);
        List<CorrelationTimelineEntry> sortedEvents = events.stream()
                .sorted(Comparator.comparing(CorrelationTimelineEntry::getTimestamp, Comparator.nullsLast(LocalDateTime::compareTo)))
                .toList();
        return collapseTimeline(sortedEvents).stream()
                .limit(maxEvents)
                .toList();
    }

    private List<CorrelationTimelineEntry> collapseTimeline(List<CorrelationTimelineEntry> sortedEvents) {
        if (sortedEvents == null || sortedEvents.size() < 3) {
            return sortedEvents == null ? List.of() : sortedEvents;
        }

        List<CorrelationTimelineEntry> collapsed = new ArrayList<>();
        List<CorrelationTimelineEntry> run = new ArrayList<>();
        for (CorrelationTimelineEntry event : sortedEvents) {
            if (run.isEmpty() || canCollapseTogether(run.get(run.size() - 1), event)) {
                run.add(event);
                continue;
            }
            flushRun(collapsed, run);
            run = new ArrayList<>();
            run.add(event);
        }
        flushRun(collapsed, run);
        return collapsed;
    }

    private boolean canCollapseTogether(CorrelationTimelineEntry previous, CorrelationTimelineEntry current) {
        if (previous == null || current == null || previous.getTimestamp() == null || current.getTimestamp() == null) {
            return false;
        }
        if (!normalize(previous.getType()).equals(normalize(current.getType()))
                || !normalize(previous.getMetric()).equals(normalize(current.getMetric()))) {
            return false;
        }
        if (!normalize(previous.getType()).startsWith("TELEMETRY")) {
            return false;
        }
        return java.time.Duration.between(previous.getTimestamp(), current.getTimestamp()).toMinutes() <= 2;
    }

    private void flushRun(List<CorrelationTimelineEntry> output, List<CorrelationTimelineEntry> run) {
        if (run.size() < 3) {
            output.addAll(run);
            return;
        }

        CorrelationTimelineEntry first = run.get(0);
        CorrelationTimelineEntry last = run.get(run.size() - 1);
        double min = run.stream()
                .map(CorrelationTimelineEntry::getValue)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0.0);
        double max = run.stream()
                .map(CorrelationTimelineEntry::getValue)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        output.add(new CorrelationTimelineEntry(
                first.getTimestamp(),
                first.getMachineIdentifier(),
                "TIMELINE_COLLAPSED",
                first.getMetric(),
                last.getValue(),
                String.format(
                        Locale.ROOT,
                        "%s remained elevated between %s and %s across %d samples (range %.1f%%-%.1f%%)",
                        title(first.getMetric()),
                        first.getTimestamp().toLocalTime().withNano(0),
                        last.getTimestamp().toLocalTime().withNano(0),
                        run.size(),
                        min,
                        max
                ),
                "timeline-collapse"
        ));
    }

    private void addTelemetryEvents(
            List<CorrelationTimelineEntry> events,
            AlertEventMessage alertEventMessage,
            List<TelemetrySnapshotDetailResponse> telemetry,
            AiInvestigationContextBuilder.AiInvestigationContext context
    ) {
        String metric = normalize(alertEventMessage.getAlertType());
        TelemetrySnapshotDetailResponse previous = null;

        for (TelemetrySnapshotDetailResponse snapshot : telemetry) {
            Double metricValue = metricValue(metric, snapshot);
            if (metricValue != null && isSignificantSpike(metric, previous, snapshot)) {
                events.add(new CorrelationTimelineEntry(
                        snapshot.getTimestamp(),
                        snapshot.getMachineIdentifier(),
                        "TELEMETRY_SPIKE",
                        metric,
                        metricValue,
                        title(metric) + " increased to " + formatPercent(metricValue),
                        "telemetry"
                ));
            }

            if (metricValue != null && isRecovery(metric, alertEventMessage.getMetricValue(), snapshot)) {
                events.add(new CorrelationTimelineEntry(
                        snapshot.getTimestamp(),
                        snapshot.getMachineIdentifier(),
                        "TELEMETRY_RECOVERY",
                        metric,
                        metricValue,
                        title(metric) + " recovered to " + formatPercent(metricValue),
                        "telemetry"
                ));
            }

            addProcessEvents(events, snapshot);
            addContextMetricEvents(events, alertEventMessage, snapshot);
            previous = snapshot;
        }

        if (context.recentMetricTrend() != null && !context.recentMetricTrend().contains("unavailable") && !telemetry.isEmpty()) {
            TelemetrySnapshotDetailResponse latest = telemetry.get(telemetry.size() - 1);
            events.add(new CorrelationTimelineEntry(
                    latest.getTimestamp(),
                    latest.getMachineIdentifier(),
                    "TELEMETRY_CONTEXT",
                    metric,
                    metricValue(metric, latest),
                    context.recentMetricTrend(),
                    "telemetry"
            ));
        }
    }

    private void addProcessEvents(List<CorrelationTimelineEntry> events, TelemetrySnapshotDetailResponse snapshot) {
        ProcessMetricResponse topCpu = topCpuProcess(snapshot.getProcessMetrics());
        if (topCpu != null && topCpu.getCpuPercent() != null && topCpu.getCpuPercent() >= 25.0) {
            events.add(new CorrelationTimelineEntry(
                    snapshot.getTimestamp(),
                    snapshot.getMachineIdentifier(),
                    "PROCESS_SPIKE",
                    "CPU",
                    topCpu.getCpuPercent(),
                    topCpu.getProcessName() + " reached " + formatPercent(topCpu.getCpuPercent()) + " CPU",
                    "processMetrics"
            ));
        }

        ProcessMetricResponse topMemory = topMemoryProcess(snapshot.getProcessMetrics());
        if (topMemory != null && topMemory.getMemoryPercent() != null && topMemory.getMemoryPercent() >= 15.0) {
            events.add(new CorrelationTimelineEntry(
                    snapshot.getTimestamp(),
                    snapshot.getMachineIdentifier(),
                    "PROCESS_SPIKE",
                    "MEMORY",
                    topMemory.getMemoryPercent(),
                    topMemory.getProcessName() + " reached " + formatPercent(topMemory.getMemoryPercent()) + " memory",
                    "processMetrics"
            ));
        }
    }

    private void addContextMetricEvents(
            List<CorrelationTimelineEntry> events,
            AlertEventMessage alertEventMessage,
            TelemetrySnapshotDetailResponse snapshot
    ) {
        String focusMetric = normalize(alertEventMessage.getAlertType());
        if (!"MEMORY".equals(focusMetric) && snapshot.getMemoryUsage() != null && snapshot.getMemoryUsage() >= 80.0) {
            events.add(new CorrelationTimelineEntry(
                    snapshot.getTimestamp(),
                    snapshot.getMachineIdentifier(),
                    "TELEMETRY_CONTEXT",
                    "MEMORY",
                    snapshot.getMemoryUsage(),
                    "Memory remained elevated at " + formatPercent(snapshot.getMemoryUsage()),
                    "telemetry"
            ));
        }
        if (!"DISK".equals(focusMetric) && snapshot.getDiskUsage() != null && snapshot.getDiskUsage() >= 85.0) {
            events.add(new CorrelationTimelineEntry(
                    snapshot.getTimestamp(),
                    snapshot.getMachineIdentifier(),
                    "TELEMETRY_CONTEXT",
                    "DISK",
                    snapshot.getDiskUsage(),
                    "Disk usage stayed high at " + formatPercent(snapshot.getDiskUsage()),
                    "telemetry"
            ));
        }
        if (!"CPU".equals(focusMetric) && snapshot.getCpuUsage() != null && snapshot.getCpuUsage() >= 75.0) {
            events.add(new CorrelationTimelineEntry(
                    snapshot.getTimestamp(),
                    snapshot.getMachineIdentifier(),
                    "TELEMETRY_CONTEXT",
                    "CPU",
                    snapshot.getCpuUsage(),
                    "CPU stayed elevated at " + formatPercent(snapshot.getCpuUsage()),
                    "telemetry"
            ));
        }
    }

    private void addAlertEvents(List<CorrelationTimelineEntry> events, String machineIdentifier, List<AlertResponse> alerts) {
        for (AlertResponse alert : alerts) {
            if (alert.getCreatedAt() != null) {
                events.add(new CorrelationTimelineEntry(
                        alert.getCreatedAt(),
                        machineIdentifier,
                        "ALERT_TRIGGERED",
                        normalize(alert.getAlertType()),
                        null,
                        normalize(alert.getSeverity()) + " " + normalize(alert.getAlertType()) + " alert created",
                        "alert-engine"
                ));
            }
            if (alert.getResolvedAt() != null) {
                events.add(new CorrelationTimelineEntry(
                        alert.getResolvedAt(),
                        machineIdentifier,
                        "ALERT_RESOLVED",
                        normalize(alert.getAlertType()),
                        null,
                        normalize(alert.getAlertType()) + " alert resolved",
                        "alert-engine"
                ));
            }
        }
    }

    private void addAnomalyEvents(List<CorrelationTimelineEntry> events, String machineIdentifier, List<Anomaly> anomalies) {
        for (Anomaly anomaly : anomalies) {
            events.add(new CorrelationTimelineEntry(
                    anomaly.getDetectedAt(),
                    machineIdentifier,
                    "ANOMALY_DETECTED",
                    normalize(anomaly.getEventType()),
                    anomaly.getMetricValue(),
                    normalize(anomaly.getSeverity()) + " anomaly detected for " + normalize(anomaly.getEventType()).toLowerCase(Locale.ROOT)
                            + " at " + formatPercent(anomaly.getMetricValue()),
                    "anomaly"
            ));
        }
    }

    List<TelemetrySnapshotDetailResponse> fetchTelemetry(String machineIdentifier) {
        try {
            List<TelemetrySnapshotDetailResponse> response = monitoringApiClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/telemetry/snapshots")
                            .queryParam("machineIdentifier", machineIdentifier)
                            .build())
                    .retrieve()
                    .body(TELEMETRY_LIST_TYPE);
            return response == null ? List.of() : response;
        } catch (Exception exception) {
            return List.of();
        }
    }

    List<AlertResponse> fetchAlerts(String machineIdentifier) {
        try {
            List<AlertResponse> response = alertEngineClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/alerts")
                            .queryParam("machineIdentifier", machineIdentifier)
                            .build())
                    .retrieve()
                    .body(ALERT_LIST_TYPE);
            return response == null ? List.of() : response;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private boolean withinWindow(LocalDateTime timestamp, LocalDateTime start, LocalDateTime end) {
        return timestamp != null && !timestamp.isBefore(start) && !timestamp.isAfter(end);
    }

    private boolean isSignificantSpike(String metric, TelemetrySnapshotDetailResponse previous, TelemetrySnapshotDetailResponse current) {
        Double currentValue = metricValue(metric, current);
        if (currentValue == null) {
            return false;
        }
        if (previous == null) {
            return meetsHighThreshold(metric, currentValue);
        }
        Double previousValue = metricValue(metric, previous);
        return previousValue != null && currentValue - previousValue >= 10.0;
    }

    private boolean isRecovery(String metric, Double alertValue, TelemetrySnapshotDetailResponse current) {
        Double currentValue = metricValue(metric, current);
        return alertValue != null && currentValue != null && alertValue - currentValue >= 10.0;
    }

    private boolean meetsHighThreshold(String metric, Double value) {
        return switch (metric) {
            case "CPU" -> value >= 75.0;
            case "MEMORY" -> value >= 80.0;
            case "DISK" -> value >= 85.0;
            default -> false;
        };
    }

    private Double metricValue(String metric, TelemetrySnapshotDetailResponse snapshot) {
        if (snapshot == null) {
            return null;
        }
        return switch (metric) {
            case "CPU" -> snapshot.getCpuUsage();
            case "MEMORY" -> snapshot.getMemoryUsage();
            case "DISK" -> snapshot.getDiskUsage();
            default -> null;
        };
    }

    private ProcessMetricResponse topCpuProcess(List<ProcessMetricResponse> processMetrics) {
        if (processMetrics == null || processMetrics.isEmpty()) {
            return null;
        }
        return processMetrics.stream()
                .filter(item -> item.getCpuPercent() != null)
                .max(Comparator.comparing(ProcessMetricResponse::getCpuPercent))
                .orElse(null);
    }

    private ProcessMetricResponse topMemoryProcess(List<ProcessMetricResponse> processMetrics) {
        if (processMetrics == null || processMetrics.isEmpty()) {
            return null;
        }
        return processMetrics.stream()
                .filter(item -> item.getMemoryPercent() != null)
                .max(Comparator.comparing(ProcessMetricResponse::getMemoryPercent))
                .orElse(null);
    }

    private String normalize(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String title(String metric) {
        String normalized = normalize(metric).toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String formatPercent(Double value) {
        return value == null ? "unknown" : String.format(Locale.ROOT, "%.1f%%", value);
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return requestFactory;
    }
}
