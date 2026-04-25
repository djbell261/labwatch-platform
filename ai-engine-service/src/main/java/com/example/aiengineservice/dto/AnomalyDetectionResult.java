package com.example.aiengineservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnomalyDetectionResult {

    private final boolean anomalous;
    private final double rollingAverage;
    private final double standardDeviation;
    private final double zScore;
    private final int sampleSize;
}
