package com.example.monitoringapi.repository;

import com.example.monitoringapi.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    Optional<Agent> findByAgentId(String agentId);

    Optional<Agent> findByMachine_MachineIdAndAgentTokenHash(String machineIdentifier, String agentTokenHash);

    List<Agent> findAllByMachine_MachineId(String machineIdentifier);
}
