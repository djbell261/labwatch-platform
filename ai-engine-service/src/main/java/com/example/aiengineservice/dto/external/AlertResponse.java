package com.example.aiengineservice.dto.external;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertResponse {

    private Long id;
    private String alertType;
    private String severity;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
