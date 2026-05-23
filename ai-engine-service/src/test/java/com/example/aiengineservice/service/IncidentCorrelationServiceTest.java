package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import com.example.aiengineservice.entity.AiInvestigationEntity;
import com.example.aiengineservice.repository.AiInvestigationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentCorrelationServiceTest {

    @Test
    void reusesActiveIncidentWhenContributorAndWindowMatch() {
        AiInvestigationRepository repository = mock(AiInvestigationRepository.class);
        AiInvestigationEntity existing = new AiInvestigationEntity();
        existing.setIncidentId("incident-existing");
        existing.setIncidentGroupKey("machine-01|google chrome helper|CPU+MEMORY");
        existing.setIncidentStatus("ACTIVE");
        existing.setMachineIdentifier("machine-01");
        existing.setCreatedAt(LocalDateTime.of(2026, 5, 20, 12, 2));
        existing.setAlertType("CPU");
        existing.setSuspectedContributor("Google Chrome Helper");

        when(repository.findTop20ByMachineIdentifierAndIncidentStatusInOrderByCreatedAtDesc(
                "machine-01",
                List.of("ACTIVE", "INVESTIGATING", "RECOVERING")
        )).thenReturn(List.of(existing));

        IncidentCorrelationService service = new IncidentCorrelationService(repository, 5);
        IncidentCorrelationService.IncidentCorrelationResult result = service.correlate(
                alert(),
                context(process()),
                List.of()
        );

        assertEquals("incident-existing", result.incidentId());
        assertEquals("INVESTIGATING", result.incidentStatus());
        assertEquals("Google Chrome Helper", result.suspectedContributor());
    }

    private AlertEventMessage alert() {
        return new AlertEventMessage(
                42L,
                "machine-01",
                "machine-01.local",
                "CPU",
                "HIGH",
                "ACTIVE",
                85.0,
                null,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 12, 4)
        );
    }

    private AiInvestigationContextBuilder.AiInvestigationContext context(ProcessMetricResponse process) {
        TelemetrySnapshotDetailResponse telemetry = new TelemetrySnapshotDetailResponse();
        telemetry.setMachineIdentifier("machine-01");
        telemetry.setTimestamp(LocalDateTime.of(2026, 5, 20, 12, 4));
        telemetry.setCpuUsage(85.0);
        telemetry.setMemoryUsage(84.0);
        telemetry.setProcessMetrics(List.of(process));

        return new AiInvestigationContextBuilder.AiInvestigationContext(
                telemetry,
                List.of(telemetry),
                List.of(),
                List.of(),
                "Recent CPU trend moved from 40.0% to 85.0% across 5 snapshots",
                process,
                process,
                process
        );
    }

    private ProcessMetricResponse process() {
        ProcessMetricResponse process = new ProcessMetricResponse();
        process.setProcessName("Google Chrome Helper");
        process.setCpuPercent(70.0);
        process.setMemoryPercent(20.0);
        return process;
    }
}
