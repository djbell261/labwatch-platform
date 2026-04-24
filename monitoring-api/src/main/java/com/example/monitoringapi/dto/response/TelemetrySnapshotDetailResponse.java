package com.example.monitoringapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TelemetrySnapshotDetailResponse {

    private Long id;
    private UUID snapshotId;
    private String machineIdentifier;
    private String hostname;
    private String osType;
    private String osVersion;
    private Long uptimeSeconds;
    private LocalDateTime timestamp;
    private BigDecimal cpuUsage;
    private BigDecimal memoryUsage;
    private BigDecimal diskUsage;
    private String source;
    private List<ProcessMetricResponse> processMetrics;
}
