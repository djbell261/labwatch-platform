package com.example.monitoringapi.service;

import com.example.monitoringapi.dto.request.AgentRegistrationRequest;
import com.example.monitoringapi.dto.request.CreateHealthEventRequest;
import com.example.monitoringapi.dto.request.TelemetrySnapshotRequest;
import com.example.monitoringapi.dto.response.MachineResponse;
import com.example.monitoringapi.entity.Machine;
import com.example.monitoringapi.entity.User;
import com.example.monitoringapi.exception.ResourceNotFoundException;
import com.example.monitoringapi.exception.UnauthorizedException;
import com.example.monitoringapi.repository.MachineRepository;
import com.example.monitoringapi.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MachineService {

    private final MachineRepository machineRepository;
    private final CurrentUserService currentUserService;

    public MachineService(MachineRepository machineRepository, CurrentUserService currentUserService) {
        this.machineRepository = machineRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<MachineResponse> getAllMachines() {
        List<Machine> machines = currentUserService.isAuthEnabled()
                ? machineRepository.findAllByOwner_Id(currentUserService.getRequiredUser().getId())
                : machineRepository.findAll();

        return machines.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MachineResponse> getAvailableMachines() {
        if (currentUserService.isAuthEnabled()) {
            currentUserService.getRequiredUser();
        }

        return machineRepository.findAllByOwnerIsNull().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MachineResponse getMachineByIdentifier(String machineIdentifier) {
        return toResponse(requireAccessibleMachine(machineIdentifier));
    }

    @Transactional
    public MachineResponse claimMachine(String machineIdentifier) {
        if (!currentUserService.isAuthEnabled()) {
            throw new UnauthorizedException("Machine claiming requires user authentication");
        }

        User currentUser = currentUserService.getRequiredUser();
        Machine machine = machineRepository.findByMachineId(machineIdentifier.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Machine not found for " + machineIdentifier));

        if (machine.getOwner() != null) {
            if (machine.getOwner().getId().equals(currentUser.getId())) {
                return toResponse(machine);
            }
            throw new UnauthorizedException("Machine is already owned by another user");
        }

        machine.setOwner(currentUser);
        return toResponse(machineRepository.save(machine));
    }

    @Transactional
    public MachineResponse unclaimMachine(String machineIdentifier) {
        if (!currentUserService.isAuthEnabled()) {
            throw new UnauthorizedException("Machine unclaiming requires user authentication");
        }

        User currentUser = currentUserService.getRequiredUser();
        Machine machine = machineRepository.findByMachineIdAndOwner_Id(machineIdentifier.trim(), currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Machine not found for " + machineIdentifier));

        machine.setOwner(null);
        return toResponse(machineRepository.save(machine));
    }

    @Transactional(readOnly = true)
    public Machine requireAccessibleMachine(String machineIdentifier) {
        if (!currentUserService.isAuthEnabled()) {
            return machineRepository.findByMachineId(machineIdentifier.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Machine not found for " + machineIdentifier));
        }

        User currentUser = currentUserService.getRequiredUser();
        return machineRepository.findByMachineIdAndOwner_Id(machineIdentifier.trim(), currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Machine not found for " + machineIdentifier));
    }

    public Machine getOrCreateMachine(CreateHealthEventRequest request) {
        return machineRepository.findByMachineId(request.getMachineIdentifier())
                .map(existing -> updateMachineMetadata(existing, request))
                .orElseGet(() -> createMachine(request));
    }

    public Machine getOrCreateMachine(TelemetrySnapshotRequest request) {
        return machineRepository.findByMachineId(request.getMachineIdentifier())
                .map(existing -> updateMachineMetadata(existing, request))
                .orElseGet(() -> createMachine(request));
    }

    public Machine getOrCreateMachine(AgentRegistrationRequest request) {
        return machineRepository.findByMachineId(request.getMachineIdentifier().trim())
                .map(existing -> updateMachineMetadata(existing, request))
                .orElseGet(() -> createMachine(request));
    }

    private Machine updateMachineMetadata(Machine machine, CreateHealthEventRequest request) {
        machine.setHostname(request.getHostname());
        machine.setLocation(request.getLocation());
        machine.setStatus("ONLINE");
        machine.setLastSeen(LocalDateTime.now());
        return machineRepository.save(machine);
    }

    private Machine createMachine(CreateHealthEventRequest request) {
        Machine machine = new Machine();
        machine.setMachineId(request.getMachineIdentifier());
        machine.setHostname(request.getHostname());
        machine.setLocation(request.getLocation());
        machine.setStatus("ONLINE");
        machine.setLastSeen(LocalDateTime.now());
        return machineRepository.save(machine);
    }

    private Machine updateMachineMetadata(Machine machine, TelemetrySnapshotRequest request) {
        machine.setHostname(request.getHostname());
        machine.setOsType(request.getOsType());
        machine.setOsVersion(request.getOsVersion());
        machine.setLastUptimeSeconds(request.getUptimeSeconds());
        machine.setLastTelemetrySource(request.getSource());
        machine.setStatus("ONLINE");
        machine.setLastSeen(request.getTimestamp());
        return machineRepository.save(machine);
    }

    private Machine createMachine(TelemetrySnapshotRequest request) {
        Machine machine = new Machine();
        machine.setMachineId(request.getMachineIdentifier());
        machine.setHostname(request.getHostname());
        machine.setOsType(request.getOsType());
        machine.setOsVersion(request.getOsVersion());
        machine.setLastUptimeSeconds(request.getUptimeSeconds());
        machine.setLastTelemetrySource(request.getSource());
        machine.setStatus("ONLINE");
        machine.setLastSeen(request.getTimestamp());
        return machineRepository.save(machine);
    }

    private Machine updateMachineMetadata(Machine machine, AgentRegistrationRequest request) {
        machine.setHostname(request.getHostname());
        machine.setOsType(request.getOsType());
        machine.setOsVersion(request.getOsVersion());
        machine.setStatus("ONLINE");
        machine.setLastSeen(LocalDateTime.now());
        return machineRepository.save(machine);
    }

    private Machine createMachine(AgentRegistrationRequest request) {
        Machine machine = new Machine();
        machine.setMachineId(request.getMachineIdentifier().trim());
        machine.setHostname(request.getHostname().trim());
        machine.setOsType(request.getOsType().trim());
        machine.setOsVersion(request.getOsVersion().trim());
        machine.setStatus("ONLINE");
        machine.setLastSeen(LocalDateTime.now());
        return machineRepository.save(machine);
    }

    private MachineResponse toResponse(Machine machine) {
        int agentCount = machine.getAgents() == null ? 0 : machine.getAgents().size();
        boolean owned = machine.getOwner() != null;

        return new MachineResponse(
                machine.getId(),
                machine.getMachineId(),
                machine.getHostname(),
                machine.getOsType(),
                machine.getOsVersion(),
                machine.getStatus(),
                machine.getLastSeen(),
                agentCount,
                owned,
                owned ? machine.getOwner().getUserId() : null,
                owned ? machine.getOwner().getDisplayName() : null,
                machine.getCreatedAt()
        );
    }
}
