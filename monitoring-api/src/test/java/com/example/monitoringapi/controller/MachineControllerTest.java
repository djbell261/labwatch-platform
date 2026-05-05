package com.example.monitoringapi.controller;

import com.example.monitoringapi.dto.request.AgentRegistrationRequest;
import com.example.monitoringapi.repository.AgentRepository;
import com.example.monitoringapi.repository.HealthEventRepository;
import com.example.monitoringapi.repository.MachineRepository;
import com.example.monitoringapi.repository.TelemetrySnapshotRepository;
import com.example.monitoringapi.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MachineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private HealthEventRepository healthEventRepository;

    @Autowired
    private TelemetrySnapshotRepository telemetrySnapshotRepository;

    @BeforeEach
    void setUp() {
        healthEventRepository.deleteAll();
        telemetrySnapshotRepository.deleteAll();
        agentRepository.deleteAll();
        machineRepository.deleteAll();

        AgentRegistrationRequest request = new AgentRegistrationRequest();
        request.setMachineIdentifier("machine-a");
        request.setHostname("host-a");
        request.setOsType("Linux");
        request.setOsVersion("6.8");
        request.setAgentVersion("1.0.0");
        agentService.registerAgent(request);
    }

    @Test
    void getMachinesReturnsMachineList() throws Exception {
        mockMvc.perform(get("/api/v1/machines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].machineIdentifier").value("machine-a"))
                .andExpect(jsonPath("$[0].hostname").value("host-a"))
                .andExpect(jsonPath("$[0].agentCount").value(1));
    }

    @Test
    void getMachineByMachineIdentifierReturnsMachine() throws Exception {
        mockMvc.perform(get("/api/v1/machines/machine-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineIdentifier").value("machine-a"))
                .andExpect(jsonPath("$.hostname").value("host-a"));
    }
}
