package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import com.example.aiengineservice.entity.Anomaly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AiInvestigationContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(AiInvestigationContextBuilder.class);
    private static final ParameterizedTypeReference<List<TelemetrySnapshotDetailResponse>> TELEMETRY_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final TelemetrySnapshotDetailResponse DEFAULT_TELEMETRY = buildDefaultTelemetry();

    private final RestClient monitoringApiClient;
    private final AnomalyQueryService anomalyQueryService;

    public AiInvestigationContextBuilder(
            AnomalyQueryService anomalyQueryService,
            @Value("${services.monitoring-api.base-url:http://monitoring-api:8089}") String monitoringApiBaseUrl,
            @Value("${services.http.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${services.http.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        this.anomalyQueryService = anomalyQueryService;
        this.monitoringApiClient = RestClient.builder()
                .baseUrl(monitoringApiBaseUrl)
                .requestFactory(buildRequestFactory(connectTimeoutMs, readTimeoutMs))
                .build();
    }

    public AiInvestigationContext build(AlertEventMessage alertEventMessage) {
        String machineIdentifier = alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : null;
        List<TelemetrySnapshotDetailResponse> recentTelemetry = fetchRecentTelemetry(machineIdentifier);
        TelemetrySnapshotDetailResponse latestTelemetry = recentTelemetry.isEmpty() ? DEFAULT_TELEMETRY : recentTelemetry.get(0);
        List<Anomaly> recentAnomalies = fetchRecentAnomalies(machineIdentifier);

        return new AiInvestigationContext(
                latestTelemetry,
                recentTelemetry,
                recentAnomalies,
                summarizeTrend(alertEventMessage, recentTelemetry),
                findTopProcess(latestTelemetry.getProcessMetrics())
        );
    }

    private List<TelemetrySnapshotDetailResponse> fetchRecentTelemetry(String machineIdentifier) {
        try {
            List<TelemetrySnapshotDetailResponse> telemetry = monitoringApiClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/v1/telemetry/snapshots");
                        if (machineIdentifier != null && !machineIdentifier.isBlank()) {
                            builder = builder.queryParam("machineIdentifier", machineIdentifier);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(TELEMETRY_LIST_TYPE);

            if (telemetry == null || telemetry.isEmpty()) {
                return List.of();
            }

            return telemetry.stream().limit(5).toList();
        } catch (Exception exception) {
            log.warn("Monitoring API recent telemetry call failed; using empty telemetry history: {}", exception.getMessage());
            return List.of();
        }
    }

    private List<Anomaly> fetchRecentAnomalies(String machineIdentifier) {
        try {
            List<Anomaly> anomalies = machineIdentifier != null && !machineIdentifier.isBlank()
                    ? anomalyQueryService.findByMachineIdentifier(machineIdentifier)
                    : List.of();
            if (anomalies == null || anomalies.isEmpty()) {
                return List.of();
            }
            return anomalies.stream().limit(3).toList();
        } catch (Exception exception) {
            log.warn("Anomaly query failed while building investigation context: {}", exception.getMessage());
            return List.of();
        }
    }

    private String summarizeTrend(AlertEventMessage alertEventMessage, List<TelemetrySnapshotDetailResponse> recentTelemetry) {
        if (alertEventMessage == null || recentTelemetry == null || recentTelemetry.size() < 2) {
            return "Recent metric trend unavailable";
        }

        Double newest = metricValueForAlertType(alertEventMessage.getAlertType(), recentTelemetry.get(0));
        Double oldest = metricValueForAlertType(alertEventMessage.getAlertType(), recentTelemetry.get(recentTelemetry.size() - 1));

        if (newest == null || oldest == null) {
            return "Recent metric trend unavailable";
        }

        double delta = newest - oldest;
        if (Math.abs(delta) < 2.0) {
            return String.format("Recent %s trend is holding steady near %.1f%%", normalizeAlertType(alertEventMessage.getAlertType()), newest);
        }

        return String.format(
                "Recent %s trend moved from %.1f%% to %.1f%% across %d snapshots",
                normalizeAlertType(alertEventMessage.getAlertType()),
                oldest,
                newest,
                recentTelemetry.size()
        );
    }

    private Double metricValueForAlertType(String alertType, TelemetrySnapshotDetailResponse telemetry) {
        if (telemetry == null) {
            return null;
        }

        return switch (normalizeAlertType(alertType)) {
            case "CPU" -> telemetry.getCpuUsage();
            case "MEMORY" -> telemetry.getMemoryUsage();
            case "DISK" -> telemetry.getDiskUsage();
            default -> null;
        };
    }

    private String normalizeAlertType(String alertType) {
        return alertType == null ? "UNKNOWN" : alertType.trim().toUpperCase();
    }

    private ProcessMetricResponse findTopProcess(List<ProcessMetricResponse> processMetrics) {
        if (processMetrics == null || processMetrics.isEmpty()) {
            return null;
        }

        return processMetrics.stream()
                .max(
                        Comparator.comparing(
                                        ProcessMetricResponse::getCpuPercent,
                                        Comparator.nullsLast(Double::compareTo)
                                )
                                .thenComparing(
                                        ProcessMetricResponse::getMemoryPercent,
                                        Comparator.nullsLast(Double::compareTo)
                                )
                )
                .orElse(null);
    }

    private static TelemetrySnapshotDetailResponse buildDefaultTelemetry() {
        TelemetrySnapshotDetailResponse telemetry = new TelemetrySnapshotDetailResponse();
        telemetry.setCpuUsage(0.0);
        telemetry.setMemoryUsage(0.0);
        telemetry.setDiskUsage(0.0);
        telemetry.setTimestamp(LocalDateTime.now());
        telemetry.setProcessMetrics(List.of());
        telemetry.setHostname("unknown");
        telemetry.setMachineIdentifier("unknown");
        telemetry.setSource("fallback");
        return telemetry;
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return requestFactory;
    }

    public record AiInvestigationContext(
            TelemetrySnapshotDetailResponse latestTelemetry,
            List<TelemetrySnapshotDetailResponse> recentTelemetry,
            List<Anomaly> recentAnomalies,
            String recentMetricTrend,
            ProcessMetricResponse topProcess
    ) {
    }
}
