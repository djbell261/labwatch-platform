package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AiInvestigationResponse;
import com.example.aiengineservice.repository.AiInvestigationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiInvestigationQueryService {

    private final AiInvestigationRepository aiInvestigationRepository;

    public AiInvestigationQueryService(AiInvestigationRepository aiInvestigationRepository) {
        this.aiInvestigationRepository = aiInvestigationRepository;
    }

    public List<AiInvestigationResponse> findLatest20() {
        return aiInvestigationRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(AiInvestigationResponse::fromEntity)
                .toList();
    }

    public List<AiInvestigationResponse> findByMachineIdentifier(String machineIdentifier) {
        return aiInvestigationRepository.findByMachineIdentifierOrderByCreatedAtDesc(machineIdentifier)
                .stream()
                .map(AiInvestigationResponse::fromEntity)
                .toList();
    }

    public List<AiInvestigationResponse> findByAlertId(String alertId) {
        return aiInvestigationRepository.findByAlertId(alertId)
                .stream()
                .map(AiInvestigationResponse::fromEntity)
                .toList();
    }
}
