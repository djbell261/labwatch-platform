package com.example.alertengine.repository;

import com.example.alertengine.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByMachineIdAndAlertTypeAndStatus(Long machineId, String alertType, String status);

    List<Alert> findByStatus(String status);

    List<Alert> findByMachineId(Long machineId);
}