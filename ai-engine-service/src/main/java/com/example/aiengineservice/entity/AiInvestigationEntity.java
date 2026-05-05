package com.example.aiengineservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_investigations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiInvestigationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "investigation_id", nullable = false, unique = true)
    private String investigationId;

    @Column(name = "alert_id", nullable = false)
    private String alertId;

    @Column(name = "machine_identifier", nullable = false)
    private String machineIdentifier;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false, length = 4000)
    private String summary;

    @Column(name = "likely_cause", nullable = false, length = 4000)
    private String likelyCause;

    @Column(name = "recommended_action", nullable = false, length = 4000)
    private String recommendedAction;

    @Column(nullable = false)
    private String confidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "persisted_at", nullable = false)
    private LocalDateTime persistedAt;

    @PrePersist
    public void prePersist() {
        if (persistedAt == null) {
            persistedAt = LocalDateTime.now();
        }
    }
}
