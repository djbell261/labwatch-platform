package com.example.monitoringapi.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateHealthEventRequest {

    @NotBlank
    private String machineIdentifier;

    @NotBlank
    private String hostname;

    private String location;

    @NotBlank
    private String eventType;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal metricValue;

    @NotBlank
    private String status;

    @Size(max = 225)
    private String message;


}