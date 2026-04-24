package com.example.monitoringapi.repository;

import com.example.monitoringapi.entity.TelemetrySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetrySnapshotRepository extends JpaRepository<TelemetrySnapshot, Long> {
}
