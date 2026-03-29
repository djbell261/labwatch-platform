package com.example.monitoringapi.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "health_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @ManyToOne
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    @Column(name = "metric_value", precision = 5, scale = 2)
    private BigDecimal metricValue;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(length = 225)
    private String message;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
    }
}