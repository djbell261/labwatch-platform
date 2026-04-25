package com.example.aiengineservice.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInsightRequest {

    private Double cpuUsage;
    private Double memoryUsage;
    private Double diskUsage;
    private ActiveAlertsSummary activeAlerts;
    private AnomaliesSummary anomalies;
    private TopProcessSummary topProcess;
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveAlertsSummary {
        private Integer count;
        private List<String> types;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomaliesSummary {
        private Integer count;
        private List<String> severity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProcessSummary {
        private String name;
        private Double cpu;
        private Double memory;
    }
}
