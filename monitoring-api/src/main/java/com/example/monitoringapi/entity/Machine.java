package com.example.monitoringapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "machine")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Machine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToMany(mappedBy = "machine")
    private List<HealthEvent> healthEvents;

    @Column(name = "machine_id", nullable = false, unique = true)
    private String machineId;

    @Column(nullable = false)
    private String hostname;

    @Column
    private String location;

    @Column(name = "os_type")
    private String osType;

    @Column(name = "os_version")
    private String osVersion;

    @Column(name = "last_uptime_seconds")
    private Long lastUptimeSeconds;

    @Column(name = "last_telemetry_source")
    private String lastTelemetrySource;

    @Column(nullable = false)
    private String status;

    @Column
    private LocalDateTime lastSeen;

    @Column
    private LocalDateTime createdAt;


    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
