package com.example.monitoringapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRegistrationRequest {

    @NotBlank
    private String machineIdentifier;

    @NotBlank
    private String hostname;

    @NotBlank
    private String osType;

    @NotBlank
    private String osVersion;

    @NotBlank
    private String agentVersion;
}
