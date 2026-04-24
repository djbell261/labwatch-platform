package com.example.monitoringapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ProcessMetricResponse {

    private String processName;
    private BigDecimal cpuPercent;
    private BigDecimal memoryPercent;
}
