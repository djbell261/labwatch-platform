package com.example.monitoringapi.controller;

import com.example.monitoringapi.dto.message.HealthEventMessage;
import com.example.monitoringapi.entity.HealthEvent;
import com.example.monitoringapi.entity.Machine;
import com.example.monitoringapi.entity.TelemetrySnapshot;
import com.example.monitoringapi.kafka.HealthEventProducer;
import com.example.monitoringapi.repository.HealthEventRepository;
import com.example.monitoringapi.repository.MachineRepository;
import com.example.monitoringapi.repository.TelemetrySnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TelemetrySnapshotControllerTest {

    private static final String VALID_SNAPSHOT_TEMPLATE = """
            {
              "machineIdentifier": "%s",
              "hostname": "%s",
              "osType": "%s",
              "osVersion": "%s",
              "uptimeSeconds": %d,
              "timestamp": "%s",
              "cpuUsage": %.1f,
              "memoryUsage": %.1f,
              "diskUsage": %.1f,
              "source": "%s",
              "processMetrics": [
                {
                  "processName": "java",
                  "cpuPercent": 22.4,
                  "memoryPercent": 18.7
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private HealthEventRepository healthEventRepository;

    @Autowired
    private TelemetrySnapshotRepository telemetrySnapshotRepository;

    @MockBean
    private HealthEventProducer healthEventProducer;

    @BeforeEach
    void setUp() {
        healthEventRepository.deleteAll();
        telemetrySnapshotRepository.deleteAll();
        machineRepository.deleteAll();
    }

    @Test
    void createSnapshotPersistsTelemetryAndNormalizesEvents() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry/snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSnapshotJson(
                                "derwins-macbook",
                                "Derwins-MacBook-Air",
                                "Darwin",
                                "23.5.0",
                                123456,
                                "2026-04-24T14:10:00",
                                42.5,
                                68.2,
                                74.1,
                                "python-agent"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.machineIdentifier").value("derwins-macbook"))
                .andExpect(jsonPath("$.hostname").value("Derwins-MacBook-Air"))
                .andExpect(jsonPath("$.normalizedEventCount").value(3));

        List<Machine> machines = machineRepository.findAll();
        assertThat(machines).hasSize(1);
        Machine machine = machines.get(0);
        assertThat(machine.getOsType()).isEqualTo("Darwin");
        assertThat(machine.getOsVersion()).isEqualTo("23.5.0");
        assertThat(machine.getLastUptimeSeconds()).isEqualTo(123456L);
        assertThat(machine.getLastTelemetrySource()).isEqualTo("python-agent");

        List<TelemetrySnapshot> snapshots = telemetrySnapshotRepository.findAll();
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getProcessMetricsJson()).contains("java");

        List<HealthEvent> events = healthEventRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(HealthEvent::getEventType))
                .toList();
        assertThat(events).hasSize(3);
        assertThat(events)
                .extracting(HealthEvent::getEventType)
                .containsExactly("CPU", "DISK", "MEMORY");
        assertThat(events)
                .extracting(HealthEvent::getStatus)
                .containsOnly("OBSERVED");

        verify(healthEventProducer, times(3)).sendEvent(any(HealthEventMessage.class));
    }

    @Test
    void getRecentSnapshotsReturnsNewestFirst() throws Exception {
        createSnapshot("machine-a", "host-a", "2026-04-24T14:00:00", 40.0, 50.0, 60.0);
        createSnapshot("machine-a", "host-a", "2026-04-24T14:05:00", 41.0, 51.0, 61.0);
        createSnapshot("machine-b", "host-b", "2026-04-24T14:10:00", 42.0, 52.0, 62.0);

        mockMvc.perform(get("/api/v1/telemetry/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].machineIdentifier").value("machine-b"))
                .andExpect(jsonPath("$[0].timestamp").value("2026-04-24T14:10:00"))
                .andExpect(jsonPath("$[1].timestamp").value("2026-04-24T14:05:00"))
                .andExpect(jsonPath("$[2].timestamp").value("2026-04-24T14:00:00"));
    }

    @Test
    void getLatestSnapshotReturnsNewestOverall() throws Exception {
        createSnapshot("machine-a", "host-a", "2026-04-24T14:00:00", 40.0, 50.0, 60.0);
        createSnapshot("machine-b", "host-b", "2026-04-24T14:10:00", 42.0, 52.0, 62.0);

        mockMvc.perform(get("/api/v1/telemetry/snapshots/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineIdentifier").value("machine-b"))
                .andExpect(jsonPath("$.hostname").value("host-b"))
                .andExpect(jsonPath("$.timestamp").value("2026-04-24T14:10:00"))
                .andExpect(jsonPath("$.cpuUsage").value(42.0));
    }

    @Test
    void getLatestSnapshotByMachineIdentifierReturnsNewestForMachine() throws Exception {
        createSnapshot("machine-a", "host-a", "2026-04-24T14:00:00", 40.0, 50.0, 60.0);
        createSnapshot("machine-a", "host-a", "2026-04-24T14:30:00", 43.0, 53.0, 63.0);
        createSnapshot("machine-b", "host-b", "2026-04-24T14:45:00", 44.0, 54.0, 64.0);

        mockMvc.perform(get("/api/v1/telemetry/snapshots/latest")
                        .param("machineIdentifier", "machine-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineIdentifier").value("machine-a"))
                .andExpect(jsonPath("$.timestamp").value("2026-04-24T14:30:00"))
                .andExpect(jsonPath("$.cpuUsage").value(43.0));
    }

    @Test
    void createSnapshotRejectsInvalidUsageValues() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry/snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "machineIdentifier": "",
                                  "hostname": "bad-host",
                                  "osType": "Linux",
                                  "osVersion": "6.8",
                                  "uptimeSeconds": 1000,
                                  "timestamp": "2026-04-24T14:10:00",
                                  "cpuUsage": 120.0,
                                  "memoryUsage": 50.0,
                                  "diskUsage": 20.0,
                                  "source": "python-agent",
                                  "processMetrics": []
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(machineRepository.findAll()).isEmpty();
        assertThat(telemetrySnapshotRepository.findAll()).isEmpty();
        assertThat(healthEventRepository.findAll()).isEmpty();
        verifyNoInteractions(healthEventProducer);
    }

    private void createSnapshot(
            String machineIdentifier,
            String hostname,
            String timestamp,
            double cpuUsage,
            double memoryUsage,
            double diskUsage
    ) throws Exception {
        mockMvc.perform(post("/api/v1/telemetry/snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSnapshotJson(
                                machineIdentifier,
                                hostname,
                                "Linux",
                                "6.8.0",
                                1000,
                                timestamp,
                                cpuUsage,
                                memoryUsage,
                                diskUsage,
                                "python-agent"
                        )))
                .andExpect(status().isCreated());
    }

    private String validSnapshotJson(
            String machineIdentifier,
            String hostname,
            String osType,
            String osVersion,
            long uptimeSeconds,
            String timestamp,
            double cpuUsage,
            double memoryUsage,
            double diskUsage,
            String source
    ) {
        return VALID_SNAPSHOT_TEMPLATE.formatted(
                machineIdentifier,
                hostname,
                osType,
                osVersion,
                uptimeSeconds,
                timestamp,
                cpuUsage,
                memoryUsage,
                diskUsage,
                source
        );
    }
}
