package com.example.aiengineservice.service;

import com.example.aiengineservice.ai.AiInsightRequest;
import com.example.aiengineservice.ai.AiProvider;
import com.example.aiengineservice.dto.AiInvestigationEvent;
import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.dto.external.TelemetrySnapshotDetailResponse;
import com.example.aiengineservice.entity.AiInvestigationEntity;
import com.example.aiengineservice.entity.Anomaly;
import com.example.aiengineservice.kafka.AiInvestigationEventProducer;
import com.example.aiengineservice.repository.AiInvestigationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiInvestigationServiceTest {

    private RecordingAiInvestigationEventProducer producer;
    private AiInvestigationRepository repository;
    private StubAiProvider aiProvider;
    private StubAiInsightRequestBuilder aiInsightRequestBuilder;
    private StubAiInvestigationContextBuilder contextBuilder;
    private StubAiSignalCorrelationService correlationService;
    private AiInvestigationTriageComposer triageComposer;
    private AiInvestigationService aiInvestigationService;

    @BeforeEach
    void setUp() {
        producer = new RecordingAiInvestigationEventProducer();
        repository = mock(AiInvestigationRepository.class);
        when(repository.save(any(AiInvestigationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        aiProvider = new StubAiProvider();
        aiInsightRequestBuilder = new StubAiInsightRequestBuilder();
        contextBuilder = new StubAiInvestigationContextBuilder();
        correlationService = new StubAiSignalCorrelationService();
        triageComposer = new AiInvestigationTriageComposer();
        aiInvestigationService = new AiInvestigationService(
                aiProvider,
                aiInsightRequestBuilder,
                contextBuilder,
                correlationService,
                triageComposer,
                producer,
                repository,
                Clock.fixed(Instant.parse("2026-05-05T14:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void skipsLowAndMediumAlertEvents() {
        aiInvestigationService.processAlertEvent(alert("LOW", "ACTIVE"));
        aiInvestigationService.processAlertEvent(alert("MEDIUM", "ACTIVE"));

        assertEquals(0, producer.events.size());
    }

    @Test
    void skipsResolvedAlertEvents() {
        aiInvestigationService.processAlertEvent(alert("HIGH", "RESOLVED"));

        assertEquals(0, producer.events.size());
    }

    @Test
    void skipsNullAlertEvents() {
        aiInvestigationService.processAlertEvent(null);

        assertEquals(0, producer.events.size());
    }

    @Test
    void processesHighAlertEvents() {
        aiInvestigationService.processAlertEvent(alert("HIGH", "ACTIVE"));

        assertEquals(1, producer.events.size());
        verify(repository).save(any(AiInvestigationEntity.class));
        assertEquals("AI summary", producer.events.get(0).getSummary());
    }

    @Test
    void processesCriticalAlertEvents() {
        aiInvestigationService.processAlertEvent(alert("CRITICAL", "ACTIVE"));

        assertEquals(1, producer.events.size());
        assertEquals("CRITICAL", producer.events.get(0).getSeverity());
    }

    @Test
    void fallbackInvestigationIsProducedWhenAiProviderFails() {
        aiProvider.throwOnGenerate = true;

        aiInvestigationService.processAlertEvent(alert("HIGH", "ACTIVE"));

        assertEquals(1, producer.events.size());
        verify(repository).save(any(AiInvestigationEntity.class));
        AiInvestigationEvent event = producer.events.get(0);
        assertEquals("HIGH", event.getConfidenceLevel());
        org.junit.jupiter.api.Assertions.assertTrue(event.getConfidenceScore() >= 70);
        assertEquals("a chrome browser tab or extension is consuming unusually high system resources.", event.getLikelyCause());
        org.junit.jupiter.api.Assertions.assertTrue(event.getSummary().contains("WHAT'S HAPPENING"));
        org.junit.jupiter.api.Assertions.assertTrue(event.getSummary().contains("IS IT SERIOUS?"));
        assertEquals("ACTIVE", event.getIncidentStatus());
        assertEquals("chrome", event.getSuspectedContributor());
        assertEquals("Memory has dropped from 92.4% to 82.4%, so this appears more transient or already recovering.", event.getPersistenceAssessment());
        org.junit.jupiter.api.Assertions.assertTrue(event.getRecommendedChecks().contains("top memory-consuming processes"));
        org.junit.jupiter.api.Assertions.assertTrue(event.getBaselineSummary().contains("Recent baseline"));
        org.junit.jupiter.api.Assertions.assertFalse(event.getCorrelationTimeline().isEmpty());
    }

    @Test
    void investigationPublishFailureDoesNotCrash() {
        producer.throwOnPublish = true;

        assertDoesNotThrow(() -> aiInvestigationService.processAlertEvent(alert("HIGH", "ACTIVE")));
        assertEquals(0, producer.events.size());
    }

    @Test
    void persistenceFailureDoesNotStopKafkaPublish() {
        doThrow(new RuntimeException("persist failed")).when(repository).save(any(AiInvestigationEntity.class));

        assertDoesNotThrow(() -> aiInvestigationService.processAlertEvent(alert("HIGH", "ACTIVE")));
        assertEquals(1, producer.events.size());
    }

    private AlertEventMessage alert(String severity, String status) {
        return new AlertEventMessage(
                42L,
                "derwins-macbook",
                "Mac",
                "MEMORY",
                severity,
                status,
                92.4,
                null,
                null,
                null,
                LocalDateTime.of(2026, 5, 5, 13, 32)
        );
    }

    private static final class RecordingAiInvestigationEventProducer extends AiInvestigationEventProducer {

        private final java.util.List<AiInvestigationEvent> events = new java.util.ArrayList<>();
        private boolean throwOnPublish;

        private RecordingAiInvestigationEventProducer() {
            super(null, "ai-investigation-events");
        }

        @Override
        public void publish(AiInvestigationEvent aiInvestigationEvent) {
            if (throwOnPublish) {
                throw new RuntimeException("publish failed");
            }
            events.add(aiInvestigationEvent);
        }
    }

    private static final class StubAiProvider implements AiProvider {

        private boolean throwOnGenerate;

        @Override
        public String generateInsight(AiInsightRequest request) {
            if (throwOnGenerate) {
                throw new IllegalStateException("AI unavailable");
            }
            return "AI summary";
        }

        @Override
        public String generateChatResponse(String userMessage, AiInsightRequest context) {
            return "chat";
        }
    }

    private static final class StubAiInsightRequestBuilder extends AiInsightRequestBuilder {

        private StubAiInsightRequestBuilder() {
            super(null, "http://localhost:8089", "http://localhost:8088", 1000, 1000);
        }

        @Override
        public AiInsightRequest buildForEvent(
                String timestamp,
                String metric,
                double value,
                String source,
                String authorizationHeader,
                String machineIdentifier
        ) {
            return AiInsightRequest.builder()
                    .focusTimestamp(timestamp)
                    .focusMetric(metric)
                    .focusValue(value)
                    .focusSource(source)
                    .memoryUsage(92.4)
                    .build();
        }
    }

    private static final class StubAiInvestigationContextBuilder extends AiInvestigationContextBuilder {

        private StubAiInvestigationContextBuilder() {
            super(null, "http://localhost:8089", "http://localhost:8088", 1000, 1000);
        }

        @Override
        public AiInvestigationContext build(AlertEventMessage alertEventMessage) {
            TelemetrySnapshotDetailResponse telemetry = new TelemetrySnapshotDetailResponse();
            telemetry.setMachineIdentifier("derwins-macbook");
            telemetry.setHostname("Mac");
            telemetry.setTimestamp(LocalDateTime.of(2026, 5, 5, 13, 32));
            telemetry.setMemoryUsage(82.4);

            ProcessMetricResponse processMetric = new ProcessMetricResponse();
            processMetric.setProcessName("chrome");
            processMetric.setMemoryPercent(31.2);
            processMetric.setCpuPercent(11.0);
            telemetry.setProcessMetrics(List.of(processMetric));

            TelemetrySnapshotDetailResponse olderTelemetry = new TelemetrySnapshotDetailResponse();
            olderTelemetry.setMachineIdentifier("derwins-macbook");
            olderTelemetry.setHostname("Mac");
            olderTelemetry.setTimestamp(LocalDateTime.of(2026, 5, 5, 13, 20));
            olderTelemetry.setMemoryUsage(84.0);
            olderTelemetry.setProcessMetrics(List.of(processMetric));

            Anomaly anomaly = new Anomaly();
            anomaly.setAnomalyId(UUID.randomUUID());
            anomaly.setEventType("MEMORY");
            anomaly.setSeverity("HIGH");

            return new AiInvestigationContext(
                    telemetry,
                    List.of(telemetry, olderTelemetry),
                    List.of(anomaly),
                    List.of(),
                    "Recent MEMORY trend moved from 84.0% to 92.4% across 5 snapshots",
                    processMetric,
                    processMetric,
                    processMetric
            );
        }
    }

    private static final class StubAiSignalCorrelationService extends AiSignalCorrelationService {

        private StubAiSignalCorrelationService() {
            super(new StubAnomalyQueryService(), "http://localhost:8089", "http://localhost:8088", 1000, 1000, 10, 5, 12);
        }

        @Override
        public java.util.List<com.example.aiengineservice.dto.CorrelationTimelineEntry> buildTimeline(
                AlertEventMessage alertEventMessage,
                AiInvestigationContextBuilder.AiInvestigationContext context
        ) {
            return java.util.List.of(
                    new com.example.aiengineservice.dto.CorrelationTimelineEntry(
                            LocalDateTime.of(2026, 5, 5, 13, 31),
                            "derwins-macbook",
                            "PROCESS_SPIKE",
                            "MEMORY",
                            31.2,
                            "chrome reached 31.2% memory",
                            "processMetrics"
                    )
            );
        }
    }

    private static final class StubAnomalyQueryService extends AnomalyQueryService {

        private StubAnomalyQueryService() {
            super(null);
        }

        @Override
        public List<Anomaly> findByMachineIdentifier(String machineIdentifier) {
            return List.of();
        }
    }
}
