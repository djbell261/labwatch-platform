package com.example.monitoringapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TelemetrySnapshotResponse {

    private Long id;
    private UUID snapshotId;
    private String machineIdentifier;
    private String hostname;
    private LocalDateTime timestamp;
    private Integer normalizedEventCount;
}
