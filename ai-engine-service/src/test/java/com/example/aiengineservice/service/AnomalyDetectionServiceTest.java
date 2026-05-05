package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.HealthEventMessage;
import com.example.aiengineservice.entity.Anomaly;
import com.example.aiengineservice.kafka.AnomalyEventProducer;
import com.example.aiengineservice.repository.AnomalyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private AnomalyRepository anomalyRepository;

    @Mock
    private AnomalyEventProducer anomalyEventProducer;

    @Test
    void noAnomalyBeforeBaselineSeconds() {
        AnomalyDetectionService service = new AnomalyDetectionService(
                anomalyRepository,
                anomalyEventProducer,
                60,
                5,
                60,
                3.0,
                120
        );

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        for (int i = 0; i < 6; i++) {
            service.processHealthEvent(event(1L, "machine-a", "CPU", 50.0 + i, start.plusSeconds(i * 5L)));
        }

        verify(anomalyRepository, never()).save(any(Anomaly.class));
        verify(anomalyEventProducer, never()).publish(any());
    }

    @Test
    void noAnomalyBeforeMinimumSampleSize() {
        AnomalyDetectionService service = new AnomalyDetectionService(
                anomalyRepository,
                anomalyEventProducer,
                60,
                20,
                10,
                3.0,
                120
        );

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        for (int i = 0; i < 12; i++) {
            service.processHealthEvent(event(1L, "machine-a", "MEMORY", 40.0 + (i % 2), start.plusSeconds(65 + i)));
        }

        verify(anomalyRepository, never()).save(any(Anomaly.class));
        verify(anomalyEventProducer, never()).publish(any());
    }

    @Test
    void anomalyCreatedAfterBaselineAndThreshold() {
        AnomalyDetectionService service = new AnomalyDetectionService(
                anomalyRepository,
                anomalyEventProducer,
                60,
                20,
                60,
                3.0,
                120
        );
        when(anomalyRepository.findFirstByMachineIdAndEventTypeOrderByDetectedAtDesc(1L, "CPU"))
                .thenReturn(Optional.empty());

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        for (int i = 0; i < 20; i++) {
            service.processHealthEvent(event(1L, "machine-a", "CPU", 50.0 + (i % 2), start.plusSeconds(i * 3L)));
        }

        service.processHealthEvent(event(1L, "machine-a", "CPU", 90.0, start.plusSeconds(61)));

        ArgumentCaptor<Anomaly> anomalyCaptor = ArgumentCaptor.forClass(Anomaly.class);
        verify(anomalyRepository).save(anomalyCaptor.capture());
        verify(anomalyEventProducer).publish(any());

        Anomaly savedAnomaly = anomalyCaptor.getValue();
        assertThat(savedAnomaly.getMachineIdentifier()).isEqualTo("machine-a");
        assertThat(savedAnomaly.getEventType()).isEqualTo("CPU");
        assertThat(savedAnomaly.getMetricValue()).isEqualTo(90.0);
        assertThat(Math.abs(savedAnomaly.getZScore())).isGreaterThanOrEqualTo(3.0);
    }

    @Test
    void duplicateAnomalySkippedDuringCooldown() {
        AnomalyDetectionService service = new AnomalyDetectionService(
                anomalyRepository,
                anomalyEventProducer,
                60,
                20,
                60,
                3.0,
                120
        );

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        for (int i = 0; i < 20; i++) {
            service.processHealthEvent(event(1L, "machine-a", "DISK", 30.0 + (i % 2), start.plusSeconds(i * 3L)));
        }

        LocalDateTime firstAnomalyTime = start.plusSeconds(61);
        LocalDateTime secondAnomalyTime = start.plusSeconds(100);

        when(anomalyRepository.findFirstByMachineIdAndEventTypeOrderByDetectedAtDesc(1L, "DISK"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAnomaly(1L, "machine-a", "DISK", firstAnomalyTime)));

        service.processHealthEvent(event(1L, "machine-a", "DISK", 80.0, firstAnomalyTime));
        service.processHealthEvent(event(1L, "machine-a", "DISK", 85.0, secondAnomalyTime));

        verify(anomalyRepository, times(1)).save(any(Anomaly.class));
        verify(anomalyEventProducer, times(1)).publish(any());
    }

    private HealthEventMessage event(
            Long machineId,
            String machineIdentifier,
            String eventType,
            double metricValue,
            LocalDateTime createdAt
    ) {
        return new HealthEventMessage(
                UUID.randomUUID(),
                machineId,
                machineIdentifier,
                machineIdentifier + ".local",
                eventType,
                BigDecimal.valueOf(metricValue),
                "ACTIVE",
                eventType + " sample",
                createdAt
        );
    }

    private Anomaly existingAnomaly(Long machineId, String machineIdentifier, String eventType, LocalDateTime detectedAt) {
        Anomaly anomaly = new Anomaly();
        anomaly.setAnomalyId(UUID.randomUUID());
        anomaly.setSourceEventId(UUID.randomUUID());
        anomaly.setMachineId(machineId);
        anomaly.setMachineIdentifier(machineIdentifier);
        anomaly.setHostname(machineIdentifier + ".local");
        anomaly.setEventType(eventType);
        anomaly.setMetricValue(80.0);
        anomaly.setRollingAverage(30.0);
        anomaly.setStandardDeviation(5.0);
        anomaly.setZScore(5.0);
        anomaly.setSampleSize(20);
        anomaly.setSeverity("HIGH");
        anomaly.setMessage("existing anomaly");
        anomaly.setDetectedAt(detectedAt);
        return anomaly;
    }
}
