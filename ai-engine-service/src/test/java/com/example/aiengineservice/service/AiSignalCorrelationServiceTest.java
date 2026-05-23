package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.CorrelationTimelineEntry;
import com.example.aiengineservice.dto.external.AlertResponse;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import com.example.aiengineservice.entity.Anomaly;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSignalCorrelationServiceTest {

    @Test
    void buildsSortedTimelineFromNearbySignals() {
        ProcessMetricResponse chrome = new ProcessMetricResponse();
        chrome.setProcessName("Google Chrome Helper");
        chrome.setCpuPercent(76.0);
        chrome.setMemoryPercent(18.0);

        TelemetrySnapshotDetailResponse t1 = telemetry("machine-01", LocalDateTime.of(2026, 5, 20, 12, 1), 59.8, 78.0, 44.0, chrome);
        TelemetrySnapshotDetailResponse t2 = telemetry("machine-01", LocalDateTime.of(2026, 5, 20, 12, 7), 22.3, 82.2, 44.0, chrome);

        AlertResponse alert = new AlertResponse();
        alert.setAlertType("CPU");
        alert.setSeverity("HIGH");
        alert.setStatus("ACTIVE");
        alert.setCreatedAt(LocalDateTime.of(2026, 5, 20, 12, 4));

        Anomaly anomaly = new Anomaly();
        anomaly.setAnomalyId(UUID.randomUUID());
        anomaly.setMachineIdentifier("machine-01");
        anomaly.setEventType("MEMORY");
        anomaly.setMetricValue(82.2);
        anomaly.setSeverity("HIGH");
        anomaly.setDetectedAt(LocalDateTime.of(2026, 5, 20, 12, 3));

        AlertEventMessage event = new AlertEventMessage(
                11L,
                "machine-01",
                "machine-01.local",
                "CPU",
                "HIGH",
                "ACTIVE",
                59.8,
                null,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 12, 4)
        );

        AiSignalCorrelationService service = new StubCorrelationService(List.of(t2, t1), List.of(alert), List.of(anomaly));
        AiInvestigationContextBuilder.AiInvestigationContext context =
                new AiInvestigationContextBuilder.AiInvestigationContext(
                        t2,
                        List.of(t2, t1),
                        List.of(anomaly),
                        List.of(alert),
                        "Recent CPU trend moved from 59.8% to 22.3% across 5 snapshots",
                        chrome,
                        chrome,
                        chrome
                );

        List<CorrelationTimelineEntry> timeline = service.buildTimeline(event, context);

        assertTrue(timeline.size() >= 4);
        assertTrue(!timeline.get(0).getTimestamp().isAfter(timeline.get(timeline.size() - 1).getTimestamp()));
        assertEquals("ALERT_TRIGGERED", timeline.stream().filter(item -> "ALERT_TRIGGERED".equals(item.getType())).findFirst().orElseThrow().getType());
        assertTrue(timeline.stream().anyMatch(item -> "PROCESS_SPIKE".equals(item.getType())));
        assertTrue(timeline.stream().anyMatch(item -> "TELEMETRY_RECOVERY".equals(item.getType())));
        assertTrue(timeline.stream().allMatch(item -> "machine-01".equals(item.getMachineIdentifier())));
    }

    private TelemetrySnapshotDetailResponse telemetry(
            String machineIdentifier,
            LocalDateTime timestamp,
            double cpu,
            double memory,
            double disk,
            ProcessMetricResponse processMetric
    ) {
        TelemetrySnapshotDetailResponse response = new TelemetrySnapshotDetailResponse();
        response.setMachineIdentifier(machineIdentifier);
        response.setTimestamp(timestamp);
        response.setCpuUsage(cpu);
        response.setMemoryUsage(memory);
        response.setDiskUsage(disk);
        response.setProcessMetrics(List.of(processMetric));
        return response;
    }

    private static final class StubCorrelationService extends AiSignalCorrelationService {

        private final List<TelemetrySnapshotDetailResponse> telemetry;
        private final List<AlertResponse> alerts;
        private final List<Anomaly> anomalies;

        private StubCorrelationService(
                List<TelemetrySnapshotDetailResponse> telemetry,
                List<AlertResponse> alerts,
                List<Anomaly> anomalies
        ) {
            super(new StubAnomalyQueryService(anomalies), "http://localhost:8089", "http://localhost:8088", 1000, 1000, 10, 5, 12);
            this.telemetry = telemetry;
            this.alerts = alerts;
            this.anomalies = anomalies;
        }

        @Override
        List<TelemetrySnapshotDetailResponse> fetchTelemetry(String machineIdentifier) {
            return telemetry;
        }

        @Override
        List<AlertResponse> fetchAlerts(String machineIdentifier) {
            return alerts;
        }
    }

    private static final class StubAnomalyQueryService extends AnomalyQueryService {

        private final List<Anomaly> anomalies;

        private StubAnomalyQueryService(List<Anomaly> anomalies) {
            super(null);
            this.anomalies = anomalies;
        }

        @Override
        public List<Anomaly> findByMachineIdentifier(String machineIdentifier) {
            return anomalies;
        }
    }
}
