package com.example.monitoringapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class MachineResponse {

    private Long id;
    private String machineIdentifier;
    private String hostname;
    private String location;
    private String status;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;

    }