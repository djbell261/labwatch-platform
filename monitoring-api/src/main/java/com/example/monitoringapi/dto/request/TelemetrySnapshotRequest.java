package com.example.monitoringapi.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TelemetrySnapshotRequest {

    @NotBlank
    private String machineIdentifier;

    @NotBlank
    private String hostname;

    @NotBlank
    private String osType;

    @NotBlank
    private String osVersion;

    @NotNull
    private Long uptimeSeconds;

    @NotNull
    private LocalDateTime timestamp;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal cpuUsage;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal memoryUsage;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal diskUsage;

    @NotBlank
    private String source;

    @Valid
    private List<ProcessMetricRequest> processMetrics = new ArrayList<>();
}
