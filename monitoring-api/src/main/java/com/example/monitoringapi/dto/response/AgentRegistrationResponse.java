package com.example.monitoringapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AgentRegistrationResponse {

    private Long id;
    private String agentId;
    private String agentToken;
    private String machineIdentifier;
    private LocalDateTime registeredAt;
}
