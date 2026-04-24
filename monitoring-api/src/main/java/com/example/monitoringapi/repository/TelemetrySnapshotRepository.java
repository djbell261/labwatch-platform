package com.example.monitoringapi.repository;

import com.example.monitoringapi.entity.TelemetrySnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelemetrySnapshotRepository extends JpaRepository<TelemetrySnapshot, Long> {

    Page<TelemetrySnapshot> findAllByMachine_MachineId(String machineIdentifier, Pageable pageable);

    Optional<TelemetrySnapshot> findFirstByOrderByCollectedAtDescCreatedAtDesc();

    Optional<TelemetrySnapshot> findFirstByMachine_MachineIdOrderByCollectedAtDescCreatedAtDesc(String machineIdentifier);
}
