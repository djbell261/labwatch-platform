package com.example.monitoringapi.repository;

import com.example.monitoringapi.entity.TelemetrySnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelemetrySnapshotRepository extends JpaRepository<TelemetrySnapshot, Long> {

    Page<TelemetrySnapshot> findAllByMachine_MachineId(String machineIdentifier, Pageable pageable);

    Page<TelemetrySnapshot> findAllByMachine_Owner_Id(Long ownerId, Pageable pageable);

    Page<TelemetrySnapshot> findAllByMachine_MachineIdAndMachine_Owner_Id(
            String machineIdentifier,
            Long ownerId,
            Pageable pageable
    );

    Optional<TelemetrySnapshot> findFirstByOrderByCollectedAtDescCreatedAtDesc();

    Optional<TelemetrySnapshot> findFirstByMachine_MachineIdOrderByCollectedAtDescCreatedAtDesc(String machineIdentifier);

    Optional<TelemetrySnapshot> findFirstByMachine_Owner_IdOrderByCollectedAtDescCreatedAtDesc(Long ownerId);

    Optional<TelemetrySnapshot> findFirstByMachine_MachineIdAndMachine_Owner_IdOrderByCollectedAtDescCreatedAtDesc(
            String machineIdentifier,
            Long ownerId
    );
}
