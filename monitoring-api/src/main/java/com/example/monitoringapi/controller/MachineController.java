package com.example.monitoringapi.controller;

import com.example.monitoringapi.dto.response.MachineResponse;
import com.example.monitoringapi.service.MachineService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/machines", "/api/v1/machines"})
public class MachineController {

    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @GetMapping
    public List<MachineResponse> getAllMachines() {
        return machineService.getAllMachines();
    }

    @GetMapping("/available")
    public List<MachineResponse> getAvailableMachines() {
        return machineService.getAvailableMachines();
    }

    @GetMapping("/{machineIdentifier}")
    public MachineResponse getMachineByMachineIdentifier(@PathVariable String machineIdentifier) {
        return machineService.getMachineByIdentifier(machineIdentifier);
    }

    @PostMapping("/{machineIdentifier}/claim")
    public MachineResponse claimMachine(@PathVariable String machineIdentifier) {
        return machineService.claimMachine(machineIdentifier);
    }

    @DeleteMapping("/{machineIdentifier}/claim")
    public MachineResponse unclaimMachine(@PathVariable String machineIdentifier) {
        return machineService.unclaimMachine(machineIdentifier);
    }
}
