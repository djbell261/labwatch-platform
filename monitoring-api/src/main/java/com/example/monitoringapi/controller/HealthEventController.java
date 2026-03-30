package com.example.monitoringapi.controller;

import com.example.monitoringapi.dto.request.CreateHealthEventRequest;
import com.example.monitoringapi.dto.response.HealthEventResponse;
import com.example.monitoringapi.entity.HealthEvent;
import com.example.monitoringapi.repository.HealthEventRepository;
import com.example.monitoringapi.service.HealthEventIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class HealthEventController {

    private final HealthEventRepository healthEventRepository;
    private final HealthEventIngestionService healthEventIngestionService;

    public HealthEventController(HealthEventRepository healthEventRepository,
                                 HealthEventIngestionService healthEventIngestionService) {
        this.healthEventRepository = healthEventRepository;
        this.healthEventIngestionService = healthEventIngestionService;
    }

    @GetMapping
    public List<HealthEventResponse> getAllEvents() {
        return healthEventRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private HealthEventResponse toResponse(HealthEvent event) {
        return new HealthEventResponse(
                event.getId(),
                event.getEventId(),              // ✅ UUID (no .toString())
                event.getMachine().getMachineId(),
                event.getMachine().getHostname(),
                event.getEventType(),
                event.getMetricValue(),          // ✅ BigDecimal (no .doubleValue())
                event.getStatus(),
                event.getMessage(),
                event.getCreatedAt()
        );
    }
    @GetMapping("/{id}")
    public HealthEventResponse getEventById(@PathVariable Long id) {
        HealthEvent event = healthEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Health event not found"));

        return toResponse(event);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HealthEventResponse createEvent(@Valid @RequestBody CreateHealthEventRequest request) {
        return healthEventIngestionService.ingestEvent(request);
    }
}