package com.example.aiengineservice.service;

import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.HealthEventMessage;
import com.example.aiengineservice.entity.Anomaly;
import com.example.aiengineservice.kafka.AnomalyEventProducer;
import com.example.aiengineservice.kafka.AnomalyPromotedAlertProducer;
import com.example.aiengineservice.repository.AnomalyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private AnomalyPromotedAlertProducer anomalyPromotedAlertProducer;

    @Test
    void noAnomalyBeforeBaselineSeconds() {
        AnomalyDetectionService service = service(60, 5, 60, 3.0, 120, true, true, 300);

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        for (int i = 0; i < 6; i++) {
            service.processHealthEvent(event(1L, "machine-a", "CPU", 50.0 + i, start.plusSeconds(i * 5L)));
        }

        verify(anomalyRepository, never()).save(any(Anomaly.class));
        verify(anomalyEventProducer, never()).publish(any());
        verify(anomalyPromotedAlertProducer, never()).publish(any());
    }

    @Test
    void noAnomalyBeforeMinimumSampleSize() {
        AnomalyDetectionService service = service(60, 20, 10, 3.0, 120, true, true, 300);

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        for (int i = 0; i < 12; i++) {
            service.processHealthEvent(event(1L, "machine-a", "MEMORY", 40.0 + (i % 2), start.plusSeconds(65 + i)));
        }

        verify(anomalyRepository, never()).save(any(Anomaly.class));
        verify(anomalyEventProducer, never()).publish(any());
        verify(anomalyPromotedAlertProducer, never()).publish(any());
    }

    @Test
    void lowAnomalyDoesNotPromote() {
        AnomalyDetectionService service = service(60, 5, 0, 1.5, 0, true, true, 300);
        when(anomalyRepository.findFirstByMachineIdAndEventTypeOrderByDetectedAtDesc(1L, "CPU"))
                .thenReturn(Optional.empty());

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        seedBaseline(service, start, "CPU");

        service.processHealthEvent(event(1L, "machine-a", "CPU", 51.0, start.plusSeconds(10)));

        ArgumentCaptor<Anomaly> anomalyCaptor = ArgumentCaptor.forClass(Anomaly.class);
        verify(anomalyRepository).save(anomalyCaptor.capture());
        verify(anomalyEventProducer).publish(any());
        verify(anomalyPromotedAlertProducer, never()).publish(any());
        assertThat(anomalyCaptor.getValue().getSeverity()).isEqualTo("LOW");
    }

    @Test
    void mediumAnomalyDoesNotPromote() {
        AnomalyDetectionService service = service(60, 5, 0, 1.5, 0, true, true, 300);
        when(anomalyRepository.findFirstByMachineIdAndEventTypeOrderByDetectedAtDesc(1L, "CPU"))
                .thenReturn(Optional.empty());

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        seedBaseline(service, start, "CPU");

        service.processHealthEvent(event(1L, "machine-a", "CPU", 51.5, start.plusSeconds(10)));

        ArgumentCaptor<Anomaly> anomalyCaptor = ArgumentCaptor.forClass(Anomaly.class);
        verify(anomalyRepository).save(anomalyCaptor.capture());
        verify(anomalyEventProducer).publish(any());
        verify(anomalyPromotedAlertProducer, never()).publish(any());
        assertThat(anomalyCaptor.getValue().getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void highAnomalyPromotesToAlertEvent() {
        AnomalyDetectionService service = service(60, 5, 0, 1.5, 0, true, true, 300);
        when(anomalyRepository.findFirstByMachineIdAndEventTypeOrderByDetectedAtDesc(1L, "CPU"))
                .thenReturn(Optional.empty());

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        seedBaseline(service, start, "CPU");

        service.processHealthEvent(event(1L, "machine-a", "CPU", 52.0, start.plusSeconds(10)));

        ArgumentCaptor<AlertEventMessage> promotedAlertCaptor = ArgumentCaptor.forClass(AlertEventMessage.class);
        verify(anomalyPromotedAlertProducer).publish(promotedAlertCaptor.capture());
        AlertEventMessage promotedAlert = promotedAlertCaptor.getValue();

        assertThat(promotedAlert.getAlertType()).isEqualTo("CPU");
        assertThat(promotedAlert.getSeverity()).isEqualTo("HIGH");
        assertThat(promotedAlert.getStatus()).isEqualTo("ACTIVE");
        assertThat(promotedAlert.getAnomalyId()).isNotBlank();
        assertThat(promotedAlert.getZScore()).isNotNull();
        assertThat(promotedAlert.getMessage()).contains("CPU anomaly detected");
    }

    @Test
    void duplicateHighAnomalyInsidePromotionCooldownDoesNotPromoteAgain() {
        AnomalyDetectionService service = service(60, 5, 0, 1.5, 0, true, true, 300);
        when(anomalyRepository.findFirstByMachineIdAndEventTypeOrderByDetectedAtDesc(1L, "CPU"))
                .thenReturn(Optional.empty());

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        seedBaseline(service, start, "CPU");

        service.processHealthEvent(event(1L, "machine-a", "CPU", 52.0, start.plusSeconds(10)));
        service.processHealthEvent(event(1L, "machine-a", "CPU", 54.0, start.plusSeconds(100)));

        verify(anomalyPromotedAlertProducer, times(1)).publish(any(AlertEventMessage.class));
        verify(anomalyRepository, times(2)).save(any(Anomaly.class));
        verify(anomalyEventProducer, times(2)).publish(any());
    }

    @Test
    void highAnomalyAfterPromotionCooldownPromotesAgain() {
        AnomalyDetectionService service = service(60, 5, 0, 1.5, 0, true, true, 300);
        when(anomalyRepository.findFirstByMachineIdAndEventTypeOrderByDetectedAtDesc(1L, "CPU"))
                .thenReturn(Optional.empty());

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        seedBaseline(service, start, "CPU");

        service.processHealthEvent(event(1L, "machine-a", "CPU", 52.0, start.plusSeconds(10)));
        service.processHealthEvent(event(1L, "machine-a", "CPU", 54.0, start.plusSeconds(311)));

        verify(anomalyPromotedAlertProducer, times(2)).publish(any(AlertEventMessage.class));
    }

    @Test
    void publishFailureDoesNotCrashAnomalyDetection() {
        AnomalyDetectionService service = service(60, 5, 0, 1.5, 0, true, true, 300);
        when(anomalyRepository.findFirstByMachineIdAndEventTypeOrderByDetectedAtDesc(1L, "CPU"))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new RuntimeException("publish failed"))
                .when(anomalyPromotedAlertProducer)
                .publish(any(AlertEventMessage.class));

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        seedBaseline(service, start, "CPU");

        assertDoesNotThrow(() ->
                service.processHealthEvent(event(1L, "machine-a", "CPU", 52.0, start.plusSeconds(10)))
        );

        verify(anomalyRepository).save(any(Anomaly.class));
        verify(anomalyEventProducer).publish(any());
        verify(anomalyPromotedAlertProducer).publish(any(AlertEventMessage.class));
    }

    private AnomalyDetectionService service(
            int rollingWindowSize,
            int minimumSampleSize,
            long minimumBaselineSeconds,
            double zScoreThreshold,
            long anomalyCooldownSeconds,
            boolean debugMode,
            boolean promotionEnabled,
            long promotionCooldownSeconds
    ) {
        return new AnomalyDetectionService(
                anomalyRepository,
                anomalyEventProducer,
                anomalyPromotedAlertProducer,
                rollingWindowSize,
                minimumSampleSize,
                minimumBaselineSeconds,
                zScoreThreshold,
                anomalyCooldownSeconds,
                debugMode,
                promotionEnabled,
                promotionCooldownSeconds
        );
    }

    private void seedBaseline(AnomalyDetectionService service, LocalDateTime start, String eventType) {
        service.processHealthEvent(event(1L, "machine-a", eventType, 49.0, start.plusSeconds(0)));
        service.processHealthEvent(event(1L, "machine-a", eventType, 50.0, start.plusSeconds(1)));
        service.processHealthEvent(event(1L, "machine-a", eventType, 51.0, start.plusSeconds(2)));
        service.processHealthEvent(event(1L, "machine-a", eventType, 50.0, start.plusSeconds(3)));
        service.processHealthEvent(event(1L, "machine-a", eventType, 50.0, start.plusSeconds(4)));
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
}
