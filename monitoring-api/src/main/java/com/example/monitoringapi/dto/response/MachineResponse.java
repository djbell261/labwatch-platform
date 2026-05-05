package com.example.monitoringapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MachineResponse {

    private Long id;
    private String machineIdentifier;
    private String hostname;
    private String osType;
    private String osVersion;
    private String status;
    private LocalDateTime lastSeenAt;
    private Integer agentCount;
    private boolean owned;
    private String ownerUserId;
    private String ownerDisplayName;
    private LocalDateTime createdAt;
}
