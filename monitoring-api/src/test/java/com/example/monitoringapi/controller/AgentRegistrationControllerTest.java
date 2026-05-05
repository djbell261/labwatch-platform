package com.example.monitoringapi.controller;

import com.example.monitoringapi.repository.AgentRepository;
import com.example.monitoringapi.repository.MachineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private MachineRepository machineRepository;

    @BeforeEach
    void setUp() {
        agentRepository.deleteAll();
        machineRepository.deleteAll();
    }

    @Test
    void registerAgentCreatesMachineAndReturnsToken() throws Exception {
        mockMvc.perform(post("/api/v1/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "machineIdentifier": "derwins-macbook",
                                  "hostname": "Derwins-MacBook-Air.local",
                                  "osType": "Darwin",
                                  "osVersion": "23.5.0",
                                  "agentVersion": "1.0.0"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agentId").isNotEmpty())
                .andExpect(jsonPath("$.agentToken").isNotEmpty())
                .andExpect(jsonPath("$.machineIdentifier").value("derwins-macbook"))
                .andExpect(jsonPath("$.registeredAt").isNotEmpty());

        assertThat(machineRepository.findAll()).hasSize(1);
        assertThat(agentRepository.findAll()).hasSize(1);
        assertThat(agentRepository.findAll().get(0).getAgentTokenHash()).isNotBlank();
    }
}
