package com.example.aiengineservice.repository;

import com.example.aiengineservice.entity.AiInvestigationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiInvestigationRepository extends JpaRepository<AiInvestigationEntity, Long> {

    List<AiInvestigationEntity> findTop20ByOrderByCreatedAtDesc();

    List<AiInvestigationEntity> findByMachineIdentifierOrderByCreatedAtDesc(String machineIdentifier);

    List<AiInvestigationEntity> findTop20ByMachineIdentifierAndIncidentStatusInOrderByCreatedAtDesc(
            String machineIdentifier,
            List<String> incidentStatuses
    );

    List<AiInvestigationEntity> findByAlertId(String alertId);

    List<AiInvestigationEntity> findByIncidentIdOrderByCreatedAtDesc(String incidentId);
}
