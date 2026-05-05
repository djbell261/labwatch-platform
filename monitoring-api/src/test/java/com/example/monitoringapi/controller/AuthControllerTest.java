package com.example.monitoringapi.controller;

import com.example.monitoringapi.dto.request.AgentRegistrationRequest;
import com.example.monitoringapi.entity.User;
import com.example.monitoringapi.repository.AgentRepository;
import com.example.monitoringapi.repository.HealthEventRepository;
import com.example.monitoringapi.repository.MachineRepository;
import com.example.monitoringapi.repository.TelemetrySnapshotRepository;
import com.example.monitoringapi.repository.UserRepository;
import com.example.monitoringapi.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "labwatch.auth.enabled=true",
        "jwt.secret=test-jwt-secret-1234567890abcdefghijklmnop"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private TelemetrySnapshotRepository telemetrySnapshotRepository;

    @Autowired
    private HealthEventRepository healthEventRepository;

    @Autowired
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        healthEventRepository.deleteAll();
        telemetrySnapshotRepository.deleteAll();
        agentRepository.deleteAll();
        machineRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerUserReturnsTokenAndHashesPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123",
                                  "displayName": "Derwin"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.displayName").value("Derwin"));

        User savedUser = userRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("password123");
        assertThat(savedUser.getPasswordHash()).startsWith("$2");
    }

    @Test
    void loginUserReturnsToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123",
                                  "displayName": "Derwin"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void claimMachineAssignsUnownedMachineToCurrentUser() throws Exception {
        registerMachine("machine-a");
        String token = registerAndExtractToken("owner@example.com", "Derwin");

        mockMvc.perform(post("/api/v1/machines/machine-a/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineIdentifier").value("machine-a"))
                .andExpect(jsonPath("$.owned").value(true))
                .andExpect(jsonPath("$.ownerDisplayName").value("Derwin"));

        mockMvc.perform(get("/api/v1/machines")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].machineIdentifier").value("machine-a"));
    }

    @Test
    void claimMachineRejectsIfAlreadyOwnedByAnotherUser() throws Exception {
        registerMachine("machine-b");
        String ownerToken = registerAndExtractToken("owner@example.com", "Owner");
        String otherToken = registerAndExtractToken("other@example.com", "Other");

        mockMvc.perform(post("/api/v1/machines/machine-b/claim")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/machines/machine-b/claim")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Machine is already owned by another user"));
    }

    @Test
    void unclaimMachineRemovesOwnership() throws Exception {
        registerMachine("machine-c");
        String token = registerAndExtractToken("owner@example.com", "Owner");

        mockMvc.perform(post("/api/v1/machines/machine-c/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/machines/machine-c/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(false));
    }

    private void registerMachine(String machineIdentifier) {
        AgentRegistrationRequest request = new AgentRegistrationRequest();
        request.setMachineIdentifier(machineIdentifier);
        request.setHostname(machineIdentifier + ".local");
        request.setOsType("Linux");
        request.setOsVersion("6.8");
        request.setAgentVersion("1.0.0");
        agentService.registerAgent(request);
    }

    private String registerAndExtractToken(String email, String displayName) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "displayName": "%s"
                                }
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return response.split("\"token\":\"")[1].split("\"")[0];
    }
}
