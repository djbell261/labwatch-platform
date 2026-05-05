package com.example.monitoringapi.service;

import com.example.monitoringapi.dto.request.AgentRegistrationRequest;
import com.example.monitoringapi.dto.response.AgentRegistrationResponse;
import com.example.monitoringapi.entity.Agent;
import com.example.monitoringapi.entity.Machine;
import com.example.monitoringapi.exception.UnauthorizedException;
import com.example.monitoringapi.repository.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AgentService {

    public static final String AGENT_TOKEN_HEADER = "X-Agent-Token";

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRepository agentRepository;
    private final MachineService machineService;
    private final boolean agentAuthEnabled;

    public AgentService(
            AgentRepository agentRepository,
            MachineService machineService,
            @Value("${labwatch.agent-auth.enabled:false}") boolean agentAuthEnabled
    ) {
        this.agentRepository = agentRepository;
        this.machineService = machineService;
        this.agentAuthEnabled = agentAuthEnabled;
    }

    @Transactional
    public AgentRegistrationResponse registerAgent(AgentRegistrationRequest request) {
        Machine machine = machineService.getOrCreateMachine(request);
        String rawToken = generateToken();

        Agent agent = new Agent();
        agent.setAgentId(UUID.randomUUID().toString());
        agent.setAgentTokenHash(hashToken(rawToken));
        agent.setMachine(machine);
        agent.setAgentVersion(request.getAgentVersion().trim());
        agent.setStatus("ACTIVE");
        agent.setRegisteredAt(LocalDateTime.now());
        agent.setLastSeenAt(LocalDateTime.now());

        Agent savedAgent = agentRepository.save(agent);
        log.info("registered agent for machine {}", request.getMachineIdentifier());

        return new AgentRegistrationResponse(
                savedAgent.getId(),
                savedAgent.getAgentId(),
                rawToken,
                machine.getMachineId(),
                savedAgent.getRegisteredAt()
        );
    }

    @Transactional
    public void validateTelemetryAccess(String machineIdentifier, String rawToken) {
        if (!agentAuthEnabled) {
            if (rawToken != null && !rawToken.isBlank()) {
                try {
                    touchAgent(machineIdentifier, rawToken);
                } catch (UnauthorizedException exception) {
                    log.info("ignoring invalid agent token because auth is disabled for machine {}", machineIdentifier);
                }
            }
            return;
        }

        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Missing " + AGENT_TOKEN_HEADER + " header");
        }

        touchAgent(machineIdentifier, rawToken);
    }

    @Transactional
    public void touchAgent(String machineIdentifier, String rawToken) {
        Agent agent = agentRepository.findByMachine_MachineIdAndAgentTokenHash(
                        machineIdentifier.trim(),
                        hashToken(rawToken.trim())
                )
                .orElseThrow(() -> new UnauthorizedException("Invalid agent token for machine " + machineIdentifier));

        agent.setLastSeenAt(LocalDateTime.now());
        agent.setStatus("ACTIVE");
        agentRepository.save(agent);
    }

    private String generateToken() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
