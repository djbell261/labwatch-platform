package com.example.monitoringapi.repository;

import com.example.monitoringapi.entity.Machine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MachineRepository extends JpaRepository<Machine, Long> {
    Optional<Machine> findByMachineId(String machineId);

    List<Machine> findAllByOwner_Id(Long ownerId);

    List<Machine> findAllByOwnerIsNull();

    Optional<Machine> findByMachineIdAndOwner_Id(String machineId, Long ownerId);
}
