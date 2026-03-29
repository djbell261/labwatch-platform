package com.example.monitoringapi.service;

import com.example.monitoringapi.dto.request.CreateHealthEventRequest;
import com.example.monitoringapi.entity.Machine;
import com.example.monitoringapi.repository.MachineRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MachineService {

    private final MachineRepository machineRepository;

    public MachineService(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    public Machine getOrCreateMachine(CreateHealthEventRequest request) {
        return machineRepository.findByMachineId(request.getMachineIdentifier())
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
}