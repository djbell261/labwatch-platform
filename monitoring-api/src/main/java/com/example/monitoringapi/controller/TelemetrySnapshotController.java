package com.example.monitoringapi.controller;

import com.example.monitoringapi.dto.request.TelemetrySnapshotRequest;
import com.example.monitoringapi.dto.response.TelemetrySnapshotDetailResponse;
import com.example.monitoringapi.dto.response.TelemetrySnapshotResponse;
import com.example.monitoringapi.service.TelemetrySnapshotIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/telemetry/snapshots")
public class TelemetrySnapshotController {

    private final TelemetrySnapshotIngestionService telemetrySnapshotIngestionService;

    public TelemetrySnapshotController(TelemetrySnapshotIngestionService telemetrySnapshotIngestionService) {
        this.telemetrySnapshotIngestionService = telemetrySnapshotIngestionService;
    }

    @GetMapping
    public List<TelemetrySnapshotDetailResponse> getRecentSnapshots(
            @RequestParam(required = false) String machineIdentifier
    ) {
        return telemetrySnapshotIngestionService.getRecentSnapshots(machineIdentifier);
    }

    @GetMapping("/latest")
    public TelemetrySnapshotDetailResponse getLatestSnapshot(
            @RequestParam(required = false) String machineIdentifier
    ) {
        return telemetrySnapshotIngestionService.getLatestSnapshot(machineIdentifier);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TelemetrySnapshotResponse createSnapshot(@Valid @RequestBody TelemetrySnapshotRequest request) {
        return telemetrySnapshotIngestionService.ingestSnapshot(request);
    }
}
