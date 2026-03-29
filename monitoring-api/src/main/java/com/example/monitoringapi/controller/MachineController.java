package com.example.monitoringapi.controller;

import com.example.monitoringapi.entity.Machine;
import com.example.monitoringapi.repository.MachineRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/machines")
public class MachineController {

    private final MachineRepository machineRepository;

    public MachineController(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    @GetMapping
    public List<Machine> getAllMachines() {
        return machineRepository.findAll();
    }

    @GetMapping("/{id}")
    public Machine getMachineById(@PathVariable Long id) {
        return machineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Machine not found with id " + id));
    }

    @PostMapping
    public Machine createMachine(@RequestBody Machine machine) {
        return machineRepository.save(machine);
    }

    @PutMapping("/{id}")
    public Machine updateMachine(@PathVariable Long id, @RequestBody Machine updatedMachine) {
        return machineRepository.findById(id)
                .map(machine -> {
                    machine.setMachineId(updatedMachine.getMachineId());
                    machine.setHostname(updatedMachine.getHostname());
                    machine.setLocation(updatedMachine.getLocation());
                    machine.setStatus(updatedMachine.getStatus());
                    machine.setLastSeen(updatedMachine.getLastSeen());
                    return machineRepository.save(machine);
                })
                .orElseThrow(() -> new RuntimeException("Machine not found with id " + id));
    }

    @DeleteMapping("/{id}")
    public void deleteMachineById(@PathVariable Long id) {
        machineRepository.deleteById(id);
    }
}