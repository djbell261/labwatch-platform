package com.example.aiengineservice.dto.external;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TelemetrySnapshotDetailResponse {

    private Long id;
    private UUID snapshotId;
    private String machineIdentifier;
    private String hostname;
    private String osType;
    private String osVersion;
    private Long uptimeSeconds;
    private LocalDateTime timestamp;
    private Double cpuUsage;
    private Double memoryUsage;
    private Double diskUsage;
    private String source;
    private List<ProcessMetricResponse> processMetrics;
}
