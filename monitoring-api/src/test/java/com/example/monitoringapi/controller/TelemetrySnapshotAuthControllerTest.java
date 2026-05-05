package com.example.monitoringapi.controller;

import com.example.monitoringapi.repository.AgentRepository;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "labwatch.agent-auth.enabled=true")
@AutoConfigureMockMvc
class TelemetrySnapshotAuthControllerTest {

    private static final String REGISTRATION_REQUEST = """
            {
              "machineIdentifier": "machine-auth",
              "hostname": "auth-host",
              "osType": "Linux",
              "osVersion": "6.8",
              "agentVersion": "1.0.0"
            }
            """;

    private static final String SNAPSHOT_REQUEST = """
            {
              "machineIdentifier": "machine-auth",
              "hostname": "auth-host",
              "osType": "Linux",
              "osVersion": "6.8",
              "uptimeSeconds": 1000,
              "timestamp": "2026-04-25T14:10:00",
              "cpuUsage": 42.5,
              "memoryUsage": 68.2,
              "diskUsage": 74.1,
              "source": "python-agent",
              "processMetrics": []
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private HealthEventRepository healthEventRepository;

    @Autowired
    private TelemetrySnapshotRepository telemetrySnapshotRepository;

    @MockBean
    private com.example.monitoringapi.kafka.HealthEventProducer healthEventProducer;

    @BeforeEach
    void setUp() {
        healthEventRepository.deleteAll();
        telemetrySnapshotRepository.deleteAll();
        agentRepository.deleteAll();
        machineRepository.deleteAll();
    }

    @Test
    void createSnapshotAcceptsValidAgentTokenWhenAuthEnabled() throws Exception {
        String response = mockMvc.perform(post("/api/v1/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION_REQUEST))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = response.split("\"agentToken\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/v1/telemetry/snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Agent-Token", token)
                        .content(SNAPSHOT_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.machineIdentifier").value("machine-auth"));
    }

    @Test
    void createSnapshotRejectsInvalidAgentTokenWhenAuthEnabled() throws Exception {
        mockMvc.perform(post("/api/v1/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION_REQUEST))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/telemetry/snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Agent-Token", "bad-token")
                        .content(SNAPSHOT_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid agent token for machine machine-auth"));

        verifyNoInteractions(healthEventProducer);
    }
}
