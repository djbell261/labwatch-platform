package com.example.aiengineservice.controller;

import com.example.aiengineservice.dto.AiInvestigationResponse;
import com.example.aiengineservice.service.AiInvestigationQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/investigations")
public class AiInvestigationController {

    private final AiInvestigationQueryService aiInvestigationQueryService;

    public AiInvestigationController(AiInvestigationQueryService aiInvestigationQueryService) {
        this.aiInvestigationQueryService = aiInvestigationQueryService;
    }

    @GetMapping
    public List<AiInvestigationResponse> getLatestInvestigations() {
        return aiInvestigationQueryService.findLatest20();
    }

    @GetMapping("/machine/{machineIdentifier}")
    public List<AiInvestigationResponse> getByMachineIdentifier(@PathVariable String machineIdentifier) {
        return aiInvestigationQueryService.findByMachineIdentifier(machineIdentifier);
    }

    @GetMapping("/alert/{alertId}")
    public List<AiInvestigationResponse> getByAlertId(@PathVariable String alertId) {
        return aiInvestigationQueryService.findByAlertId(alertId);
    }

    @GetMapping("/incident/{incidentId}")
    public List<AiInvestigationResponse> getByIncidentId(@PathVariable String incidentId) {
        return aiInvestigationQueryService.findByIncidentId(incidentId);
    }
}
