package com.example.monitoringapi.service;

import com.example.monitoringapi.dto.message.HealthEventMessage;
import com.example.monitoringapi.dto.request.ProcessMetricRequest;
import com.example.monitoringapi.dto.request.TelemetrySnapshotRequest;
import com.example.monitoringapi.dto.response.TelemetrySnapshotResponse;
import com.example.monitoringapi.entity.HealthEvent;
import com.example.monitoringapi.entity.Machine;
import com.example.monitoringapi.entity.TelemetrySnapshot;
import com.example.monitoringapi.kafka.HealthEventProducer;
import com.example.monitoringapi.repository.HealthEventRepository;
import com.example.monitoringapi.repository.TelemetrySnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TelemetrySnapshotIngestionService {

    private final MachineService machineService;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final HealthEventRepository healthEventRepository;
    private final HealthEventProducer healthEventProducer;
    private final ObjectMapper objectMapper;

    public TelemetrySnapshotIngestionService(
            MachineService machineService,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            HealthEventRepository healthEventRepository,
            HealthEventProducer healthEventProducer,
            ObjectMapper objectMapper
    ) {
        this.machineService = machineService;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.healthEventRepository = healthEventRepository;
        this.healthEventProducer = healthEventProducer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TelemetrySnapshotResponse ingestSnapshot(TelemetrySnapshotRequest request) {
        Machine machine = machineService.getOrCreateMachine(request);

        TelemetrySnapshot snapshot = new TelemetrySnapshot();
        snapshot.setMachine(machine);
        snapshot.setHostname(request.getHostname());
        snapshot.setOsType(request.getOsType());
        snapshot.setOsVersion(request.getOsVersion());
        snapshot.setUptimeSeconds(request.getUptimeSeconds());
        snapshot.setCollectedAt(request.getTimestamp());
        snapshot.setCpuUsage(request.getCpuUsage());
        snapshot.setMemoryUsage(request.getMemoryUsage());
        snapshot.setDiskUsage(request.getDiskUsage());
        snapshot.setSource(request.getSource());
        snapshot.setProcessMetricsJson(serializeProcessMetrics(request.getProcessMetrics()));

        TelemetrySnapshot savedSnapshot = telemetrySnapshotRepository.save(snapshot);
        List<HealthEvent> normalizedEvents = createNormalizedEvents(machine, request);

        normalizedEvents.forEach(event -> healthEventProducer.sendEvent(toMessage(event)));

        return new TelemetrySnapshotResponse(
                savedSnapshot.getId(),
                savedSnapshot.getSnapshotId(),
                machine.getMachineId(),
                savedSnapshot.getHostname(),
                savedSnapshot.getCollectedAt(),
                normalizedEvents.size()
        );
    }

    private List<HealthEvent> createNormalizedEvents(Machine machine, TelemetrySnapshotRequest request) {
        List<HealthEvent> events = List.of(
                buildEvent(machine, "CPU", request.getCpuUsage(), request),
                buildEvent(machine, "MEMORY", request.getMemoryUsage(), request),
                buildEvent(machine, "DISK", request.getDiskUsage(), request)
        );
        return healthEventRepository.saveAll(events);
    }

    private HealthEvent buildEvent(
            Machine machine,
            String eventType,
            BigDecimal metricValue,
            TelemetrySnapshotRequest request
    ) {
        HealthEvent event = new HealthEvent();
        event.setMachine(machine);
        event.setEventType(eventType);
        event.setMetricValue(metricValue);
        event.setStatus("OBSERVED");
        event.setMessage(buildEventMessage(eventType, request.getSource()));
        event.setCreatedAt(request.getTimestamp());
        return event;
    }

    private String buildEventMessage(String eventType, String source) {
        return eventType + " usage snapshot collected from " + source;
    }

    private String serializeProcessMetrics(List<ProcessMetricRequest> processMetrics) {
        try {
            return objectMapper.writeValueAsString(processMetrics);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize process metrics", exception);
        }
    }

    private HealthEventMessage toMessage(HealthEvent event) {
        HealthEventMessage message = new HealthEventMessage();
        message.setEventId(event.getEventId());
        message.setMachineId(event.getMachine().getId());
        message.setMachineIdentifier(event.getMachine().getMachineId());
        message.setHostname(event.getMachine().getHostname());
        message.setEventType(event.getEventType());
        message.setMetricValue(event.getMetricValue());
        message.setStatus(event.getStatus());
        message.setMessage(event.getMessage());
        message.setCreatedAt(event.getCreatedAt());
        return message;
    }
}
