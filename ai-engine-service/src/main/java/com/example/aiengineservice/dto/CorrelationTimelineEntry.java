package com.example.aiengineservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationTimelineEntry {

    private LocalDateTime timestamp;
    private String machineIdentifier;
    private String type;
    private String metric;
    private Double value;
    private String description;
    private String source;
}
