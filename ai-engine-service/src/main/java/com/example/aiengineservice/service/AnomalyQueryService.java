package com.example.aiengineservice.service;

import com.example.aiengineservice.entity.Anomaly;
import com.example.aiengineservice.exception.ResourceNotFoundException;
import com.example.aiengineservice.repository.AnomalyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnomalyQueryService {

    private final AnomalyRepository anomalyRepository;

    public AnomalyQueryService(AnomalyRepository anomalyRepository) {
        this.anomalyRepository = anomalyRepository;
    }

    public List<Anomaly> findAll() {
        return anomalyRepository.findAll()
                .stream()
                .sorted((left, right) -> right.getDetectedAt().compareTo(left.getDetectedAt()))
                .toList();
    }

    public Anomaly findById(Long id) {
        return anomalyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anomaly not found for id " + id));
    }

    public List<Anomaly> findByMachineId(Long machineId) {
        return anomalyRepository.findByMachineIdOrderByDetectedAtDesc(machineId);
    }

    public List<Anomaly> findByEventType(String eventType) {
        return anomalyRepository.findByEventTypeOrderByDetectedAtDesc(eventType.trim().toUpperCase());
    }

    public List<Anomaly> findByMachineIdAndEventType(Long machineId, String eventType) {
        return anomalyRepository.findByMachineIdAndEventTypeOrderByDetectedAtDesc(
                machineId,
                eventType.trim().toUpperCase()
        );
    }
}
