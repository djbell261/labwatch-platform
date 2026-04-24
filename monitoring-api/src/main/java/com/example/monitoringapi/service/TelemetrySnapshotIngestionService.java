package com.example.monitoringapi.service;

import com.example.monitoringapi.dto.message.HealthEventMessage;
import com.example.monitoringapi.dto.request.ProcessMetricRequest;
import com.example.monitoringapi.dto.request.TelemetrySnapshotRequest;
import com.example.monitoringapi.dto.response.ProcessMetricResponse;
import com.example.monitoringapi.dto.response.TelemetrySnapshotDetailResponse;
import com.example.monitoringapi.dto.response.TelemetrySnapshotResponse;
import com.example.monitoringapi.entity.HealthEvent;
import com.example.monitoringapi.entity.Machine;
import com.example.monitoringapi.entity.TelemetrySnapshot;
import com.example.monitoringapi.kafka.HealthEventProducer;
import com.example.monitoringapi.repository.HealthEventRepository;
import com.example.monitoringapi.repository.TelemetrySnapshotRepository;
import com.example.monitoringapi.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Service
public class TelemetrySnapshotIngestionService {

    private static final int DEFAULT_RECENT_LIMIT = 50;
    private static final Sort RECENT_SNAPSHOT_SORT = Sort.by(
            Sort.Order.desc("collectedAt"),
            Sort.Order.desc("createdAt")
    );

    private final MachineService machineService;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final HealthEventRepository healthEventRepository;
    private final HealthEventProducer healthEventProducer;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public TelemetrySnapshotIngestionService(
            MachineService machineService,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            HealthEventRepository healthEventRepository,
            HealthEventProducer healthEventProducer,
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.machineService = machineService;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.healthEventRepository = healthEventRepository;
        this.healthEventProducer = healthEventProducer;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
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
        TelemetrySnapshotDetailResponse detailResponse = toDetailResponse(savedSnapshot);

        normalizedEvents.forEach(event -> healthEventProducer.sendEvent(toMessage(event)));
        messagingTemplate.convertAndSend("/topic/telemetry", detailResponse);

        return new TelemetrySnapshotResponse(
                savedSnapshot.getId(),
                savedSnapshot.getSnapshotId(),
                machine.getMachineId(),
                savedSnapshot.getHostname(),
                savedSnapshot.getCollectedAt(),
                normalizedEvents.size()
        );
    }

    @Transactional(readOnly = true)
    public List<TelemetrySnapshotDetailResponse> getRecentSnapshots(String machineIdentifier) {
        PageRequest pageRequest = PageRequest.of(0, DEFAULT_RECENT_LIMIT, RECENT_SNAPSHOT_SORT);

        List<TelemetrySnapshot> snapshots = isBlank(machineIdentifier)
                ? telemetrySnapshotRepository.findAll(pageRequest).getContent()
                : telemetrySnapshotRepository.findAllByMachine_MachineId(machineIdentifier.trim(), pageRequest).getContent();

        return snapshots.stream()
                .map(this::toDetailResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TelemetrySnapshotDetailResponse getLatestSnapshot(String machineIdentifier) {
        TelemetrySnapshot snapshot = isBlank(machineIdentifier)
                ? telemetrySnapshotRepository.findFirstByOrderByCollectedAtDescCreatedAtDesc()
                .orElseThrow(() -> new ResourceNotFoundException("No telemetry snapshots found"))
                : telemetrySnapshotRepository.findFirstByMachine_MachineIdOrderByCollectedAtDescCreatedAtDesc(machineIdentifier.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No telemetry snapshots found for machineIdentifier " + machineIdentifier
                ));

        return toDetailResponse(snapshot);
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

    private TelemetrySnapshotDetailResponse toDetailResponse(TelemetrySnapshot snapshot) {
        return new TelemetrySnapshotDetailResponse(
                snapshot.getId(),
                snapshot.getSnapshotId(),
                snapshot.getMachine().getMachineId(),
                snapshot.getHostname(),
                snapshot.getOsType(),
                snapshot.getOsVersion(),
                snapshot.getUptimeSeconds(),
                snapshot.getCollectedAt(),
                snapshot.getCpuUsage(),
                snapshot.getMemoryUsage(),
                snapshot.getDiskUsage(),
                snapshot.getSource(),
                deserializeProcessMetrics(snapshot.getProcessMetricsJson())
        );
    }

    private List<ProcessMetricResponse> deserializeProcessMetrics(String processMetricsJson) {
        if (isBlank(processMetricsJson)) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(processMetricsJson, new TypeReference<List<ProcessMetricResponse>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize process metrics", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
