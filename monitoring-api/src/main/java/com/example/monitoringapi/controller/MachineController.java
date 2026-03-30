package com.example.monitoringapi.controller;

import com.example.monitoringapi.dto.response.MachineResponse;
import com.example.monitoringapi.entity.Machine;
import com.example.monitoringapi.repository.MachineRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/machines")
public class MachineController {

    private final MachineRepository machineRepository;

    public MachineController(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    @GetMapping
    public List<MachineResponse> getAllMachines() {
        return machineRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public MachineResponse getMachineById(@PathVariable Long id) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Machine not found with id " + id));

        return toResponse(machine);
    }

    private MachineResponse toResponse(Machine machine) {
        return new MachineResponse(
                machine.getId(),
                machine.getMachineId(),
                machine.getHostname(),
                machine.getLocation(),
                machine.getStatus(),
                machine.getLastSeen(),
                machine.getCreatedAt()
        );
    }
}