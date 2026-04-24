package com.example.monitoringapi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "telemetry_snapshot")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelemetrySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, unique = true)
    private UUID snapshotId;

    @ManyToOne
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    @Column(nullable = false)
    private String hostname;

    @Column(name = "os_type", nullable = false)
    private String osType;

    @Column(name = "os_version", nullable = false)
    private String osVersion;

    @Column(name = "uptime_seconds", nullable = false)
    private Long uptimeSeconds;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "cpu_usage", nullable = false, precision = 5, scale = 2)
    private BigDecimal cpuUsage;

    @Column(name = "memory_usage", nullable = false, precision = 5, scale = 2)
    private BigDecimal memoryUsage;

    @Column(name = "disk_usage", nullable = false, precision = 5, scale = 2)
    private BigDecimal diskUsage;

    @Column(nullable = false)
    private String source;

    @Lob
    @Column(name = "process_metrics_json")
    private String processMetricsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (snapshotId == null) {
            snapshotId = UUID.randomUUID();
        }
    }
}
