package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.external.AlertResponse;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import com.example.aiengineservice.entity.Anomaly;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiInvestigationTriageComposerTest {

    private final AiInvestigationTriageComposer composer = new AiInvestigationTriageComposer();

    @Test
    void buildsProcessAwareCpuTriageForChromeHelper() {
        ProcessMetricResponse cpuProcess = new ProcessMetricResponse();
        cpuProcess.setProcessName("Google Chrome Helper");
        cpuProcess.setCpuPercent(76.0);
        cpuProcess.setMemoryPercent(22.0);

        ProcessMetricResponse memoryProcess = new ProcessMetricResponse();
        memoryProcess.setProcessName("Google Chrome Helper");
        memoryProcess.setCpuPercent(32.0);
        memoryProcess.setMemoryPercent(28.0);

        TelemetrySnapshotDetailResponse latest = new TelemetrySnapshotDetailResponse();
        latest.setMachineIdentifier("machine-01");
        latest.setCpuUsage(22.3);
        latest.setMemoryUsage(84.0);
        latest.setDiskUsage(43.0);
        latest.setTimestamp(LocalDateTime.of(2026, 5, 20, 15, 10));
        latest.setProcessMetrics(List.of(cpuProcess, memoryProcess));

        TelemetrySnapshotDetailResponse older = new TelemetrySnapshotDetailResponse();
        older.setMachineIdentifier("machine-01");
        older.setCpuUsage(59.8);
        older.setMemoryUsage(80.0);
        older.setDiskUsage(42.0);
        older.setTimestamp(LocalDateTime.of(2026, 5, 20, 15, 0));
        older.setProcessMetrics(List.of(cpuProcess, memoryProcess));

        AlertResponse memoryAlert = new AlertResponse();
        memoryAlert.setAlertType("MEMORY");
        memoryAlert.setStatus("ACTIVE");
        memoryAlert.setSeverity("HIGH");

        Anomaly anomaly = new Anomaly();
        anomaly.setAnomalyId(UUID.randomUUID());
        anomaly.setEventType("CPU");
        anomaly.setMetricValue(59.8);
        anomaly.setSeverity("HIGH");

        AiInvestigationContextBuilder.AiInvestigationContext context =
                new AiInvestigationContextBuilder.AiInvestigationContext(
                        latest,
                        List.of(latest, older),
                        List.of(anomaly),
                        List.of(memoryAlert),
                        "Recent CPU trend moved from 59.8% to 22.3% across 5 snapshots",
                        cpuProcess,
                        cpuProcess,
                        memoryProcess
                );

        AlertEventMessage alert = new AlertEventMessage(
                7L,
                "machine-01",
                "machine-01.local",
                "CPU",
                "HIGH",
                "ACTIVE",
                59.8,
                null,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 15, 0)
        );

        AiInvestigationTriageComposer.InvestigationTriage triage = composer.compose(alert, context, null, List.of());

        assertTrue(triage.likelyCause().contains("chrome browser tab or extension"));
        assertTrue(triage.summary().contains("WHAT'S HAPPENING"));
        assertTrue(triage.summary().contains("WHY THIS HAPPENS"));
        assertTrue(triage.summary().contains("IS IT SERIOUS?"));
        assertTrue(triage.summary().contains("WHAT TO DO NEXT"));
        assertTrue(triage.summary().contains("Recommended checks:"));
        assertTrue(triage.evidence().contains("Browser workload (Google Chrome Helper) is the top CPU contributor"));
        assertTrue(triage.contributingFactors().contains("developer tools"));
        assertTrue(triage.persistenceAssessment().contains("appears more transient or already recovering"));
    }

    @Test
    void translatesVirtualizationProcessForBeginnerFriendlyTriage() {
        ProcessMetricResponse cpuProcess = new ProcessMetricResponse();
        cpuProcess.setProcessName("com.apple.Virtualization.VirtualMachine");
        cpuProcess.setCpuPercent(68.0);
        cpuProcess.setMemoryPercent(18.0);

        TelemetrySnapshotDetailResponse latest = new TelemetrySnapshotDetailResponse();
        latest.setMachineIdentifier("machine-01");
        latest.setCpuUsage(72.0);
        latest.setMemoryUsage(64.0);
        latest.setDiskUsage(51.0);
        latest.setTimestamp(LocalDateTime.of(2026, 5, 20, 15, 10));
        latest.setProcessMetrics(List.of(cpuProcess));

        AlertEventMessage alert = new AlertEventMessage(
                8L,
                "machine-01",
                "machine-01.local",
                "CPU",
                "HIGH",
                "ACTIVE",
                72.0,
                null,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 15, 10)
        );

        AiInvestigationContextBuilder.AiInvestigationContext context =
                new AiInvestigationContextBuilder.AiInvestigationContext(
                        latest,
                        List.of(latest),
                        List.of(),
                        List.of(),
                        "Recent CPU trend is holding steady near 72.0%",
                        cpuProcess,
                        cpuProcess,
                        cpuProcess
                );

        AiInvestigationTriageComposer.InvestigationTriage triage = composer.compose(alert, context, null, List.of());

        assertTrue(triage.likelyCause().contains("docker containers"));
        assertTrue(triage.evidence().contains("Virtualization / Containers"));
        assertTrue(triage.recommendedChecks().contains("Inspect docker stats."));
        assertTrue(triage.summary().contains("local Kubernetes"));
        assertFalse(triage.likelyCause().contains("VirtualMachine appearing correlated"));
    }
}
