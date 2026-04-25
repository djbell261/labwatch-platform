package com.example.aiengineservice.repository;

import com.example.aiengineservice.entity.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {

    Optional<Anomaly> findByAnomalyId(UUID anomalyId);

    List<Anomaly> findByMachineIdOrderByDetectedAtDesc(Long machineId);

    List<Anomaly> findByEventTypeOrderByDetectedAtDesc(String eventType);

    List<Anomaly> findByMachineIdAndEventTypeOrderByDetectedAtDesc(Long machineId, String eventType);
}
