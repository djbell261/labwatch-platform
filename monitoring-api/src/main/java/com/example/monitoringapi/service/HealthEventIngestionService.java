package com.example.monitoringapi.service;


import com.example.monitoringapi.dto.message.HealthEventMessage;
import com.example.monitoringapi.dto.request.CreateHealthEventRequest;
import com.example.monitoringapi.dto.response.HealthEventResponse;
import com.example.monitoringapi.entity.HealthEvent;
import com.example.monitoringapi.entity.Machine;
import com.example.monitoringapi.kafka.HealthEventProducer;
import com.example.monitoringapi.repository.HealthEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthEventIngestionService {

    private final HealthEventRepository healthEventRepository;
    private final MachineService machineService;
    private final HealthEventProducer healthEventProducer;

    public HealthEventIngestionService(HealthEventRepository healthEventRepository,
                                       MachineService machineService,
                                       HealthEventProducer healthEventProducer) {
        this.healthEventRepository = healthEventRepository;
        this.machineService = machineService;
        this.healthEventProducer = healthEventProducer;
    }

    @Transactional
    public HealthEventResponse ingestEvent(CreateHealthEventRequest request) {
        Machine machine = machineService.getOrCreateMachine(request);

        HealthEvent event = new HealthEvent();
        event.setMachine(machine);
        event.setEventType(request.getEventType().toUpperCase());
        event.setMetricValue(request.getMetricValue());
        event.setStatus(request.getStatus());
        event.setMessage(request.getMessage());

        HealthEvent savedEvent = healthEventRepository.save(event);

        HealthEventMessage message = toMessage(savedEvent);
        healthEventProducer.sendEvent(message);

        return toResponse(savedEvent);
    }

    private HealthEventMessage toMessage(HealthEvent savedEvent) {
        HealthEventMessage message = new HealthEventMessage();
        message.setEventId(savedEvent.getEventId());
        message.setMachineId(savedEvent.getMachine().getId());
        message.setMachineIdentifier(savedEvent.getMachine().getMachineId());
        message.setHostname(savedEvent.getMachine().getHostname());
        message.setEventType(savedEvent.getEventType());
        message.setMetricValue(savedEvent.getMetricValue());
        message.setStatus(savedEvent.getStatus());
        message.setMessage(savedEvent.getMessage());
        message.setCreatedAt(savedEvent.getCreatedAt());
        return message;
    }

    private HealthEventResponse toResponse(HealthEvent savedEvent) {
        return new HealthEventResponse(
                savedEvent.getId(),
                savedEvent.getEventId(),
                savedEvent.getMachine().getMachineId(),
                savedEvent.getMachine().getHostname(),
                savedEvent.getEventType(),
                savedEvent.getMetricValue(),
                savedEvent.getStatus(),
                savedEvent.getMessage(),
                savedEvent.getCreatedAt()
        );
    }
}