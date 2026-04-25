package com.example.aiengineservice.dto.external;

import lombok.Data;

@Data
public class ProcessMetricResponse {

    private String processName;
    private Double cpuPercent;
    private Double memoryPercent;
}
