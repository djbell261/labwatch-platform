package com.example.aiengineservice.controller;

import com.example.aiengineservice.dto.AiInvestigationResponse;
import com.example.aiengineservice.service.AiInvestigationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AiInvestigationController.class)
class AiInvestigationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiInvestigationQueryService aiInvestigationQueryService;

    @Test
    void returnsLatestInvestigations() throws Exception {
        when(aiInvestigationQueryService.findLatest20()).thenReturn(List.of(response("inv-1", "1001", "machine-a")));

        mockMvc.perform(get("/api/investigations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].investigationId").value("inv-1"))
                .andExpect(jsonPath("$[0].alertId").value("1001"));
    }

    @Test
    void returnsInvestigationsForMachine() throws Exception {
        when(aiInvestigationQueryService.findByMachineIdentifier("machine-a"))
                .thenReturn(List.of(response("inv-2", "1002", "machine-a")));

        mockMvc.perform(get("/api/investigations/machine/machine-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].machineIdentifier").value("machine-a"));
    }

    @Test
    void returnsInvestigationsForAlert() throws Exception {
        when(aiInvestigationQueryService.findByAlertId("1003"))
                .thenReturn(List.of(response("inv-3", "1003", "machine-b")));

        mockMvc.perform(get("/api/investigations/alert/1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertId").value("1003"));
    }

    private AiInvestigationResponse response(String investigationId, String alertId, String machineIdentifier) {
        return new AiInvestigationResponse(
                investigationId,
                alertId,
                machineIdentifier,
                "MEMORY",
                "HIGH",
                "summary",
                "likely cause",
                "recommended action",
                "HIGH",
                LocalDateTime.of(2026, 5, 5, 13, 32),
                LocalDateTime.of(2026, 5, 5, 14, 0)
        );
    }
}
