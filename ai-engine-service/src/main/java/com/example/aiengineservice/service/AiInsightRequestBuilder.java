package com.example.aiengineservice.service;

import com.example.aiengineservice.ai.AiInsightRequest;
import com.example.aiengineservice.dto.external.AlertResponse;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import com.example.aiengineservice.entity.Anomaly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AiInsightRequestBuilder {

    private static final Logger log = LoggerFactory.getLogger(AiInsightRequestBuilder.class);
    private static final ParameterizedTypeReference<List<AlertResponse>> ALERT_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final TelemetrySnapshotDetailResponse DEFAULT_TELEMETRY = buildDefaultTelemetry();
    private static final AiInsightRequest.TopProcessSummary DEFAULT_TOP_PROCESS =
            AiInsightRequest.TopProcessSummary.builder()
                    .name("unknown")
                    .cpu(0.0)
                    .memory(0.0)
                    .build();

    private final RestClient monitoringApiClient;
    private final RestClient alertEngineClient;
    private final AnomalyQueryService anomalyQueryService;

    public AiInsightRequestBuilder(
            AnomalyQueryService anomalyQueryService,
            @Value("${services.monitoring-api.base-url:http://monitoring-api:8089}") String monitoringApiBaseUrl,
            @Value("${services.alert-engine.base-url:http://alert-engine:8088}") String alertEngineBaseUrl,
            @Value("${services.http.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${services.http.read-timeout-ms:3000}") int readTimeoutMs
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
    }

    public AiInsightRequest build() {
        TelemetrySnapshotDetailResponse latestTelemetry = fetchLatestTelemetry();
        List<AlertResponse> alerts = fetchAlerts();
        List<Anomaly> anomalies = fetchAnomalies();

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

        ProcessMetricResponse topProcess = findTopProcess(latestTelemetry.getProcessMetrics());

        return AiInsightRequest.builder()
                .cpuUsage(safeDouble(latestTelemetry.getCpuUsage()))
                .memoryUsage(safeDouble(latestTelemetry.getMemoryUsage()))
                .diskUsage(safeDouble(latestTelemetry.getDiskUsage()))
                .activeAlerts(
                        AiInsightRequest.ActiveAlertsSummary.builder()
                                .count(activeAlerts.size())
                                .types(alertTypes.isEmpty() ? List.of() : alertTypes)
                                .build()
                )
                .anomalies(
                        AiInsightRequest.AnomaliesSummary.builder()
                                .count(anomalies.size())
                                .severity(anomalySeverity.isEmpty() ? List.of() : anomalySeverity)
                                .build()
                )
                .topProcess(toTopProcessSummary(topProcess))
                .timestamp(latestTelemetry.getTimestamp() != null ? latestTelemetry.getTimestamp() : LocalDateTime.now())
                .build();
    }

    private TelemetrySnapshotDetailResponse fetchLatestTelemetry() {
        try {
            TelemetrySnapshotDetailResponse telemetry = monitoringApiClient.get()
                    .uri("/api/v1/telemetry/snapshots/latest")
                    .retrieve()
                    .body(TelemetrySnapshotDetailResponse.class);

            if (telemetry == null) {
                log.warn("Monitoring API returned null latest telemetry; using default telemetry fallback");
                return buildDefaultTelemetry();
            }

            return telemetry;
        } catch (Exception exception) {
            log.warn("Monitoring API call failed; using default telemetry fallback: {}", exception.getMessage());
            return buildDefaultTelemetry();
        }
    }

    private List<AlertResponse> fetchAlerts() {
        try {
            List<AlertResponse> alerts = alertEngineClient.get()
                    .uri("/api/alerts")
                    .retrieve()
                    .body(ALERT_LIST_TYPE);

            if (alerts == null) {
                log.warn("Alert engine returned null alerts list; using empty alerts fallback");
                return List.of();
            }

            return alerts;
        } catch (Exception exception) {
            log.warn("Alert engine call failed; using empty alerts fallback: {}", exception.getMessage());
            return List.of();
        }
    }

    private List<Anomaly> fetchAnomalies() {
        try {
            List<Anomaly> anomalies = anomalyQueryService.findAll();
            if (anomalies == null) {
                log.warn("Anomaly query returned null; using empty anomalies fallback");
                return List.of();
            }
            return anomalies;
        } catch (Exception exception) {
            log.warn("Anomaly query failed; using empty anomalies fallback: {}", exception.getMessage());
            return List.of();
        }
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

    private AiInsightRequest.TopProcessSummary toTopProcessSummary(ProcessMetricResponse topProcess) {
        if (topProcess == null) {
            log.info("No top process available; using default top process fallback");
            return DEFAULT_TOP_PROCESS;
        }

        return AiInsightRequest.TopProcessSummary.builder()
                .name(topProcess.getProcessName() != null && !topProcess.getProcessName().isBlank()
                        ? topProcess.getProcessName()
                        : "unknown")
                .cpu(safeDouble(topProcess.getCpuPercent()))
                .memory(safeDouble(topProcess.getMemoryPercent()))
                .build();
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
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
}
