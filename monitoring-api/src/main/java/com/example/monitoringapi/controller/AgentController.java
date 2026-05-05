package com.example.monitoringapi.controller;

import com.example.monitoringapi.dto.request.AgentRegistrationRequest;
import com.example.monitoringapi.dto.response.AgentRegistrationResponse;
import com.example.monitoringapi.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentRegistrationResponse register(@Valid @RequestBody AgentRegistrationRequest request) {
        return agentService.registerAgent(request);
    }
}
