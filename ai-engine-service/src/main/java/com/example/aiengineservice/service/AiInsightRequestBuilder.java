package com.example.aiengineservice.service;

import com.example.aiengineservice.ai.AiInsightRequest;
import com.example.aiengineservice.dto.external.AlertResponse;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import com.example.aiengineservice.entity.Anomaly;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AiInsightRequestBuilder {

    private static final ParameterizedTypeReference<List<AlertResponse>> ALERT_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient monitoringApiClient;
    private final RestClient alertEngineClient;
    private final AnomalyQueryService anomalyQueryService;

    public AiInsightRequestBuilder(
            AnomalyQueryService anomalyQueryService,
            @Value("${services.monitoring-api.base-url:http://localhost:8089}") String monitoringApiBaseUrl,
            @Value("${services.alert-engine.base-url:http://localhost:8088}") String alertEngineBaseUrl
    ) {
        this.anomalyQueryService = anomalyQueryService;
        this.monitoringApiClient = RestClient.builder().baseUrl(monitoringApiBaseUrl).build();
        this.alertEngineClient = RestClient.builder().baseUrl(alertEngineBaseUrl).build();
    }

    public AiInsightRequest build() {
        TelemetrySnapshotDetailResponse latestTelemetry = fetchLatestTelemetry();
        List<AlertResponse> alerts = fetchAlerts();
        List<Anomaly> anomalies = anomalyQueryService.findAll();

        List<AlertResponse> activeAlerts = alerts.stream()
                .filter(alert -> "ACTIVE".equalsIgnoreCase(alert.getStatus()))
                .toList();

        List<String> alertTypes = activeAlerts.stream()
                .map(AlertResponse::getAlertType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .toList();

        List<String> anomalySeverity = anomalies.stream()
                .map(Anomaly::getSeverity)
                .filter(severity -> severity != null && !severity.isBlank())
                .distinct()
                .toList();

        ProcessMetricResponse topProcess = latestTelemetry != null
                ? findTopProcess(latestTelemetry.getProcessMetrics())
                : null;

        return AiInsightRequest.builder()
                .cpuUsage(latestTelemetry != null ? latestTelemetry.getCpuUsage() : null)
                .memoryUsage(latestTelemetry != null ? latestTelemetry.getMemoryUsage() : null)
                .diskUsage(latestTelemetry != null ? latestTelemetry.getDiskUsage() : null)
                .activeAlerts(
                        AiInsightRequest.ActiveAlertsSummary.builder()
                                .count(activeAlerts.size())
                                .types(alertTypes)
                                .build()
                )
                .anomalies(
                        AiInsightRequest.AnomaliesSummary.builder()
                                .count(anomalies.size())
                                .severity(anomalySeverity)
                                .build()
                )
                .topProcess(
                        topProcess == null
                                ? null
                                : AiInsightRequest.TopProcessSummary.builder()
                                .name(topProcess.getProcessName())
                                .cpu(topProcess.getCpuPercent())
                                .memory(topProcess.getMemoryPercent())
                                .build()
                )
                .timestamp(latestTelemetry != null ? latestTelemetry.getTimestamp() : LocalDateTime.now())
                .build();
    }

    private TelemetrySnapshotDetailResponse fetchLatestTelemetry() {
        return monitoringApiClient.get()
                .uri("/api/v1/telemetry/snapshots/latest")
                .retrieve()
                .body(TelemetrySnapshotDetailResponse.class);
    }

    private List<AlertResponse> fetchAlerts() {
        List<AlertResponse> alerts = alertEngineClient.get()
                .uri("/api/alerts")
                .retrieve()
                .body(ALERT_LIST_TYPE);
        return alerts != null ? alerts : List.of();
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
}
