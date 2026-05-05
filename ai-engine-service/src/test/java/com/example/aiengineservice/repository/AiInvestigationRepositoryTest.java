package com.example.aiengineservice.repository;

import com.example.aiengineservice.entity.AiInvestigationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class AiInvestigationRepositoryTest {

    @Autowired
    private AiInvestigationRepository aiInvestigationRepository;

    @Test
    void repositoryQueriesReturnCorrectResults() {
        aiInvestigationRepository.save(investigation("inv-1", "1001", "machine-a", LocalDateTime.of(2026, 5, 5, 13, 0)));
        aiInvestigationRepository.save(investigation("inv-2", "1002", "machine-a", LocalDateTime.of(2026, 5, 5, 14, 0)));
        aiInvestigationRepository.save(investigation("inv-3", "1003", "machine-b", LocalDateTime.of(2026, 5, 5, 15, 0)));

        List<AiInvestigationEntity> latest = aiInvestigationRepository.findTop20ByOrderByCreatedAtDesc();
        List<AiInvestigationEntity> machineInvestigations =
                aiInvestigationRepository.findByMachineIdentifierOrderByCreatedAtDesc("machine-a");
        List<AiInvestigationEntity> alertInvestigations = aiInvestigationRepository.findByAlertId("1002");

        assertEquals(List.of("inv-3", "inv-2", "inv-1"),
                latest.stream().map(AiInvestigationEntity::getInvestigationId).toList());
        assertEquals(List.of("inv-2", "inv-1"),
                machineInvestigations.stream().map(AiInvestigationEntity::getInvestigationId).toList());
        assertEquals(List.of("inv-2"),
                alertInvestigations.stream().map(AiInvestigationEntity::getInvestigationId).toList());
    }

    private AiInvestigationEntity investigation(
            String investigationId,
            String alertId,
            String machineIdentifier,
            LocalDateTime createdAt
    ) {
        return new AiInvestigationEntity(
                null,
                investigationId,
                alertId,
                machineIdentifier,
                "MEMORY",
                "HIGH",
                "summary",
                "likely cause",
                "recommended action",
                "HIGH",
                createdAt,
                LocalDateTime.of(2026, 5, 5, 16, 0)
        );
    }
}
