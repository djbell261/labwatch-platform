package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.CorrelationTimelineEntry;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RootCauseConfidenceServiceTest {

    @Test
    void scoresHighWhenProcessDominatesTimelineAndBaselineRepeats() {
        ProcessMetricResponse process = new ProcessMetricResponse();
        process.setProcessName("java");
        process.setCpuPercent(68.0);
        process.setMemoryPercent(18.0);

        TelemetrySnapshotDetailResponse telemetry = new TelemetrySnapshotDetailResponse();
        telemetry.setMachineIdentifier("dev-machine");
        telemetry.setTimestamp(LocalDateTime.of(2026, 5, 20, 10, 0));
        telemetry.setCpuUsage(88.0);
        telemetry.setMemoryUsage(60.0);
        telemetry.setDiskUsage(40.0);
        telemetry.setProcessMetrics(List.of(process));

        AiInvestigationContextBuilder.AiInvestigationContext context =
                new AiInvestigationContextBuilder.AiInvestigationContext(
                        telemetry,
                        List.of(telemetry),
                        List.of(),
                        List.of(),
                        "Recent CPU trend moved from 40.0% to 88.0% across 5 snapshots",
                        process,
                        process,
                        process
                );
        AlertEventMessage alert = new AlertEventMessage(
                7L,
                "dev-machine",
                "dev-machine.local",
                "CPU",
                "HIGH",
                "ACTIVE",
                88.0,
                null,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 10, 0)
        );
        MachineBehaviorProfileService.MachineBehaviorProfile profile =
                new MachineBehaviorProfileService.MachineBehaviorProfile(
                        40.0,
                        55.0,
                        40.0,
                        5.0,
                        4.0,
                        "java",
                        true,
                        true,
                        "09:00-17:00",
                        "Recent baseline for this machine is CPU 40%.",
                        "java appears repeatedly."
                );

        RootCauseConfidenceService.ConfidenceScore score = new RootCauseConfidenceService().score(
                alert,
                context,
                profile,
                new IncidentCorrelationService.IncidentCorrelationResult(
                        "incident-1",
                        "dev-machine|java|CPU",
                        "ACTIVE",
                        "java",
                        "CPU",
                        0
                ),
                List.of(new CorrelationTimelineEntry(
                        LocalDateTime.of(2026, 5, 20, 9, 59),
                        "dev-machine",
                        "PROCESS_SPIKE",
                        "CPU",
                        68.0,
                        "java reached 68.0% CPU",
                        "processMetrics"
                ))
        );

        assertEquals("HIGH", score.level());
        assertTrue(score.score() >= 75);
        assertTrue(score.reasoning().contains("same process"));
    }
}
