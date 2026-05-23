package com.example.aiengineservice.service;

import com.example.aiengineservice.ai.AiInsightRequest;
import com.example.aiengineservice.ai.AiProvider;
import com.example.aiengineservice.dto.AiInvestigationEvent;
import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.entity.AiInvestigationEntity;
import com.example.aiengineservice.kafka.AiInvestigationEventProducer;
import com.example.aiengineservice.repository.AiInvestigationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.UUID;

@Service
public class AiInvestigationService {

    private static final Logger log = LoggerFactory.getLogger(AiInvestigationService.class);
    private static final Set<String> QUALIFYING_SEVERITIES = Set.of("HIGH", "CRITICAL");

    private final AiProvider aiProvider;
    private final AiInsightRequestBuilder aiInsightRequestBuilder;
    private final AiInvestigationContextBuilder contextBuilder;
    private final AiSignalCorrelationService correlationService;
    private final IncidentCorrelationService incidentCorrelationService;
    private final MachineBehaviorProfileService machineBehaviorProfileService;
    private final RootCauseConfidenceService rootCauseConfidenceService;
    private final AiInvestigationTriageComposer triageComposer;
    private final KnownProcessEnrichmentService processEnrichmentService;
    private final AiInvestigationEventProducer aiInvestigationEventProducer;
    private final AiInvestigationRepository aiInvestigationRepository;
    private final Clock clock;
    private final Executor aiInvestigationExecutor;
    private final MeterRegistry meterRegistry;
    private final long timeoutMs;

    @Autowired
    public AiInvestigationService(
            AiProvider aiProvider,
            AiInsightRequestBuilder aiInsightRequestBuilder,
            AiInvestigationContextBuilder contextBuilder,
            AiSignalCorrelationService correlationService,
            IncidentCorrelationService incidentCorrelationService,
            MachineBehaviorProfileService machineBehaviorProfileService,
            RootCauseConfidenceService rootCauseConfidenceService,
            AiInvestigationTriageComposer triageComposer,
            KnownProcessEnrichmentService processEnrichmentService,
            AiInvestigationEventProducer aiInvestigationEventProducer,
            AiInvestigationRepository aiInvestigationRepository,
            Clock clock,
            @Qualifier("aiInvestigationExecutor") Executor aiInvestigationExecutor,
            MeterRegistry meterRegistry,
            @Value("${app.ai-investigation.timeout-ms:15000}") long timeoutMs
    ) {
        this.aiProvider = aiProvider;
        this.aiInsightRequestBuilder = aiInsightRequestBuilder;
        this.contextBuilder = contextBuilder;
        this.correlationService = correlationService;
        this.incidentCorrelationService = incidentCorrelationService;
        this.machineBehaviorProfileService = machineBehaviorProfileService;
        this.rootCauseConfidenceService = rootCauseConfidenceService;
        this.triageComposer = triageComposer;
        this.processEnrichmentService = processEnrichmentService;
        this.aiInvestigationEventProducer = aiInvestigationEventProducer;
        this.aiInvestigationRepository = aiInvestigationRepository;
        this.clock = clock;
        this.aiInvestigationExecutor = aiInvestigationExecutor;
        this.meterRegistry = meterRegistry;
        this.timeoutMs = timeoutMs;
    }

    AiInvestigationService(
            AiProvider aiProvider,
            AiInsightRequestBuilder aiInsightRequestBuilder,
            AiInvestigationContextBuilder contextBuilder,
            AiSignalCorrelationService correlationService,
            AiInvestigationTriageComposer triageComposer,
            AiInvestigationEventProducer aiInvestigationEventProducer,
            AiInvestigationRepository aiInvestigationRepository,
            Clock clock
    ) {
        this(
                aiProvider,
                aiInsightRequestBuilder,
                contextBuilder,
                correlationService,
                new IncidentCorrelationService(aiInvestigationRepository, 5),
                new MachineBehaviorProfileService(),
                new RootCauseConfidenceService(),
                triageComposer,
                aiInvestigationEventProducer,
                aiInvestigationRepository,
                clock
        );
    }

    AiInvestigationService(
            AiProvider aiProvider,
            AiInsightRequestBuilder aiInsightRequestBuilder,
            AiInvestigationContextBuilder contextBuilder,
            AiSignalCorrelationService correlationService,
            IncidentCorrelationService incidentCorrelationService,
            MachineBehaviorProfileService machineBehaviorProfileService,
            RootCauseConfidenceService rootCauseConfidenceService,
            AiInvestigationTriageComposer triageComposer,
            AiInvestigationEventProducer aiInvestigationEventProducer,
            AiInvestigationRepository aiInvestigationRepository,
            Clock clock
    ) {
        this(
                aiProvider,
                aiInsightRequestBuilder,
                contextBuilder,
                correlationService,
                incidentCorrelationService,
                machineBehaviorProfileService,
                rootCauseConfidenceService,
                triageComposer,
                new KnownProcessEnrichmentService(),
                aiInvestigationEventProducer,
                aiInvestigationRepository,
                clock,
                Runnable::run,
                new SimpleMeterRegistry(),
                15_000L
        );
    }

    public void processAlertEvent(AlertEventMessage alertEventMessage) {
        if (!qualifies(alertEventMessage)) {
            log.info("Skipping alert event because it does not qualify for AI investigation");
            return;
        }

        try {
            CompletableFuture.runAsync(() -> processAlertEventInternal(alertEventMessage), aiInvestigationExecutor)
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(exception -> {
                        meterRegistry.counter("labwatch.ai.investigations.failed", "stage", "async").increment();
                        log.error(
                                "event=ai_investigation_async_failure machineIdentifier={} alertType={} error={}",
                                alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : "unknown",
                                alertEventMessage != null ? alertEventMessage.getAlertType() : "unknown",
                                exception.getClass().getSimpleName(),
                                exception
                        );
                        return null;
                    });
            meterRegistry.counter("labwatch.ai.investigations.started").increment();
        } catch (TaskRejectedException exception) {
            meterRegistry.counter("labwatch.ai.investigations.failed", "stage", "submission").increment();
            log.error(
                    "event=ai_investigation_rejected machineIdentifier={} alertType={} error={}",
                    alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : "unknown",
                    alertEventMessage != null ? alertEventMessage.getAlertType() : "unknown",
                    exception.getClass().getSimpleName(),
                    exception
            );
            throw exception;
        }
    }

    private void processAlertEventInternal(AlertEventMessage alertEventMessage) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info(
                "event=ai_investigation_received machineIdentifier={} severity={} status={}",
                alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : "unknown",
                alertEventMessage != null ? alertEventMessage.getSeverity() : "unknown",
                alertEventMessage != null ? alertEventMessage.getStatus() : "unknown"
        );

        try {
            AiInvestigationEvent aiInvestigationEvent = generateInvestigation(alertEventMessage);
            boolean persisted = persistInvestigation(aiInvestigationEvent);
            if (!persisted) {
                meterRegistry.counter("labwatch.ai.investigations.failed", "stage", "persistence").increment();
            }
            log.info("Publishing AI investigation event");
            aiInvestigationEventProducer.publish(aiInvestigationEvent);
            meterRegistry.counter("labwatch.ai.investigations.completed").increment();
            sample.stop(meterRegistry.timer("labwatch.ai.investigation.latency"));
        } catch (Exception exception) {
            meterRegistry.counter("labwatch.ai.investigations.failed", "stage", "execution").increment();
            sample.stop(meterRegistry.timer("labwatch.ai.investigation.latency"));
            log.error(
                    "event=ai_investigation_failed machineIdentifier={} alertType={} error={}",
                    alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : "unknown",
                    alertEventMessage != null ? alertEventMessage.getAlertType() : "unknown",
                    exception.getClass().getSimpleName(),
                    exception
            );
        }
    }

    private boolean persistInvestigation(AiInvestigationEvent aiInvestigationEvent) {
        try {
            log.info("Persisting AI investigation");
            aiInvestigationRepository.save(toEntity(aiInvestigationEvent));
            log.info("AI investigation persisted successfully");
            return true;
        } catch (Exception exception) {
            log.error("Failed to persist AI investigation", exception);
            return false;
        }
    }

    private boolean qualifies(AlertEventMessage alertEventMessage) {
        if (alertEventMessage == null) {
            return false;
        }

        String status = normalize(alertEventMessage.getStatus());
        String severity = normalize(alertEventMessage.getSeverity());
        return "ACTIVE".equals(status) && QUALIFYING_SEVERITIES.contains(severity);
    }

    private AiInvestigationEvent generateInvestigation(AlertEventMessage alertEventMessage) {
        AiInvestigationContextBuilder.AiInvestigationContext context = contextBuilder.build(alertEventMessage);
        LocalDateTime investigationCreatedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        java.util.List<com.example.aiengineservice.dto.CorrelationTimelineEntry> correlationTimeline =
                withInvestigationCreatedEvent(correlationService.buildTimeline(alertEventMessage, context), alertEventMessage, investigationCreatedAt);
        MachineBehaviorProfileService.MachineBehaviorProfile profile = machineBehaviorProfileService.build(alertEventMessage, context);
        IncidentCorrelationService.IncidentCorrelationResult incident = incidentCorrelationService.correlate(
                alertEventMessage,
                context,
                correlationTimeline
        );
        RootCauseConfidenceService.ConfidenceScore confidenceScore = rootCauseConfidenceService.score(
                alertEventMessage,
                context,
                profile,
                incident,
                correlationTimeline
        );
        try {
            AiInsightRequest request = aiInsightRequestBuilder.buildForEvent(
                    formatCreatedAt(alertEventMessage.getCreatedAt()),
                    alertEventMessage.getAlertType(),
                    alertEventMessage.getMetricValue() != null ? alertEventMessage.getMetricValue() : 0.0,
                    "alert-event",
                    null,
                    alertEventMessage.getMachineIdentifier()
            ).toBuilder()
                    .machineIdentifier(fallback(alertEventMessage.getMachineIdentifier()))
                    .recentMetricTrend(context.recentMetricTrend())
                    .topProcess(toTopProcessSummary(context.topProcess()))
                    .topCpuProcess(toTopProcessSummary(context.topCpuProcess()))
                    .topMemoryProcess(toTopProcessSummary(context.topMemoryProcess()))
                    .correlationTimeline(correlationTimeline.stream().map(entry -> {
                        String timestamp = entry.getTimestamp() != null
                                ? entry.getTimestamp().toLocalTime().withNano(0).toString()
                                : "unknown";
                        return timestamp + " — " + entry.getType() + " — " + entry.getDescription();
                    }).toList())
                    .incidentGroupKey(incident.incidentGroupKey())
                    .incidentStatus(incident.incidentStatus())
                    .suspectedContributor(incident.suspectedContributor())
                    .affectedMetrics(incident.affectedMetrics())
                    .confidenceScore(confidenceScore.score())
                    .confidenceLevel(confidenceScore.level())
                    .baselineSummary(profile.baselineSummary())
                    .historicalPatternNotes(profile.historicalPatternNotes())
                    .build();

            String summary = aiProvider.generateInsight(request);
            AiInvestigationTriageComposer.InvestigationTriage triage = triageComposer.compose(
                    alertEventMessage,
                    context,
                    summary,
                    correlationTimeline,
                    profile,
                    incident,
                    confidenceScore
            );
            AiInvestigationEvent aiInvestigationEvent = new AiInvestigationEvent(
                    UUID.randomUUID(),
                    incident.incidentId(),
                    incident.incidentGroupKey(),
                    incident.incidentStatus(),
                    alertEventMessage.getAlertId(),
                    fallback(alertEventMessage.getMachineIdentifier()),
                    fallback(alertEventMessage.getAlertType()),
                    fallback(alertEventMessage.getSeverity()),
                    triage.summary(),
                    triage.likelyCause(),
                    triage.evidence(),
                    triage.contributingFactors(),
                    triage.recommendedChecks(),
                    triage.recommendedActions(),
                    triage.urgencyAssessment(),
                    triage.persistenceAssessment(),
                    triage.monitorNext(),
                    incident.suspectedContributor(),
                    incident.affectedMetrics(),
                    confidenceScore.score(),
                    confidenceScore.level(),
                    confidenceScore.reasoning(),
                    profile.baselineSummary(),
                    profile.historicalPatternNotes(),
                    correlationTimeline,
                    confidenceScore.display(),
                    investigationCreatedAt
            );
            log.info("Generated AI investigation");
            return aiInvestigationEvent;
        } catch (Exception exception) {
            log.warn("Failed to generate AI investigation, using fallback", exception);
            return fallbackInvestigation(alertEventMessage, context);
        }
    }

    private AiInvestigationEvent fallbackInvestigation(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context
    ) {
        LocalDateTime investigationCreatedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        java.util.List<com.example.aiengineservice.dto.CorrelationTimelineEntry> correlationTimeline =
                withInvestigationCreatedEvent(correlationService.buildTimeline(alertEventMessage, context), alertEventMessage, investigationCreatedAt);
        MachineBehaviorProfileService.MachineBehaviorProfile profile = machineBehaviorProfileService.build(alertEventMessage, context);
        IncidentCorrelationService.IncidentCorrelationResult incident = incidentCorrelationService.correlate(
                alertEventMessage,
                context,
                correlationTimeline
        );
        RootCauseConfidenceService.ConfidenceScore confidenceScore = rootCauseConfidenceService.score(
                alertEventMessage,
                context,
                profile,
                incident,
                correlationTimeline
        );
        AiInvestigationTriageComposer.InvestigationTriage triage = triageComposer.compose(
                alertEventMessage,
                context,
                null,
                correlationTimeline,
                profile,
                incident,
                confidenceScore
        );
        return new AiInvestigationEvent(
                UUID.randomUUID(),
                incident.incidentId(),
                incident.incidentGroupKey(),
                incident.incidentStatus(),
                alertEventMessage != null ? alertEventMessage.getAlertId() : null,
                fallback(alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : null),
                fallback(alertEventMessage != null ? alertEventMessage.getAlertType() : null),
                fallback(alertEventMessage != null ? alertEventMessage.getSeverity() : null),
                triage.summary(),
                triage.likelyCause(),
                triage.evidence(),
                triage.contributingFactors(),
                triage.recommendedChecks(),
                triage.recommendedActions(),
                triage.urgencyAssessment(),
                triage.persistenceAssessment(),
                triage.monitorNext(),
                incident.suspectedContributor(),
                incident.affectedMetrics(),
                confidenceScore.score(),
                confidenceScore.level(),
                confidenceScore.reasoning(),
                profile.baselineSummary(),
                profile.historicalPatternNotes(),
                correlationTimeline,
                confidenceScore.display(),
                investigationCreatedAt
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String fallback(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String formatCreatedAt(LocalDateTime createdAt) {
        return createdAt == null ? "unknown" : createdAt.atOffset(ZoneOffset.UTC).toInstant().toString();
    }

    private java.util.List<com.example.aiengineservice.dto.CorrelationTimelineEntry> withInvestigationCreatedEvent(
            java.util.List<com.example.aiengineservice.dto.CorrelationTimelineEntry> correlationTimeline,
            AlertEventMessage alertEventMessage,
            LocalDateTime investigationCreatedAt
    ) {
        java.util.List<com.example.aiengineservice.dto.CorrelationTimelineEntry> events =
                new java.util.ArrayList<>(correlationTimeline != null ? correlationTimeline : java.util.List.of());
        events.add(new com.example.aiengineservice.dto.CorrelationTimelineEntry(
                investigationCreatedAt,
                fallback(alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : null),
                "INVESTIGATION_CREATED",
                fallback(alertEventMessage != null ? alertEventMessage.getAlertType() : null),
                alertEventMessage != null ? alertEventMessage.getMetricValue() : null,
                "AI investigation generated for this incident",
                "ai-investigation"
        ));
        events.sort(java.util.Comparator.comparing(com.example.aiengineservice.dto.CorrelationTimelineEntry::getTimestamp));
        return events;
    }

    private AiInsightRequest.TopProcessSummary toTopProcessSummary(
            com.example.aiengineservice.dto.external.ProcessMetricResponse processMetricResponse
    ) {
        AiInsightRequest.TopProcessSummary.TopProcessSummaryBuilder builder = AiInsightRequest.TopProcessSummary.builder()
                .name(processMetricResponse != null && hasText(processMetricResponse.getProcessName())
                        ? processMetricResponse.getProcessName()
                        : "unknown")
                .cpu(processMetricResponse != null ? processMetricResponse.getCpuPercent() : 0.0)
                .memory(processMetricResponse != null ? processMetricResponse.getMemoryPercent() : 0.0);

        if (processMetricResponse != null) {
            processEnrichmentService.enrich(processMetricResponse.getProcessName())
                    .ifPresent(insight -> builder
                            .category(insight.category())
                            .humanExplanation(insight.humanExplanation())
                            .likelyCauses(insight.likelyCauses())
                            .operatorAdvice(insight.operatorAdvice())
                            .beginnerFriendly(insight.beginnerFriendly()));
        }

        return builder.build();
    }

    private AiInvestigationEntity toEntity(AiInvestigationEvent aiInvestigationEvent) {
        return new AiInvestigationEntity(
                null,
                aiInvestigationEvent.getInvestigationId() != null ? aiInvestigationEvent.getInvestigationId().toString() : null,
                fallback(aiInvestigationEvent.getIncidentId()),
                fallback(aiInvestigationEvent.getIncidentGroupKey()),
                fallback(aiInvestigationEvent.getIncidentStatus()),
                aiInvestigationEvent.getAlertId() != null ? String.valueOf(aiInvestigationEvent.getAlertId()) : "unknown",
                fallback(aiInvestigationEvent.getMachineIdentifier()),
                fallback(aiInvestigationEvent.getAlertType()),
                fallback(aiInvestigationEvent.getSeverity()),
                fallback(aiInvestigationEvent.getSummary()),
                fallback(aiInvestigationEvent.getLikelyCause()),
                fallback(aiInvestigationEvent.getEvidence()),
                fallback(aiInvestigationEvent.getContributingFactors()),
                fallback(aiInvestigationEvent.getRecommendedChecks()),
                fallback(aiInvestigationEvent.getRecommendedAction()),
                fallback(aiInvestigationEvent.getUrgencyAssessment()),
                fallback(aiInvestigationEvent.getPersistenceAssessment()),
                fallback(aiInvestigationEvent.getMonitorNext()),
                fallback(aiInvestigationEvent.getSuspectedContributor()),
                fallback(aiInvestigationEvent.getAffectedMetrics()),
                aiInvestigationEvent.getConfidenceScore() != null ? aiInvestigationEvent.getConfidenceScore() : 0,
                fallback(aiInvestigationEvent.getConfidenceLevel()),
                fallback(aiInvestigationEvent.getConfidenceReasoning()),
                fallback(aiInvestigationEvent.getBaselineSummary()),
                fallback(aiInvestigationEvent.getHistoricalPatternNotes()),
                aiInvestigationEvent.getCorrelationTimeline() != null ? aiInvestigationEvent.getCorrelationTimeline() : java.util.List.of(),
                fallback(aiInvestigationEvent.getConfidence()),
                aiInvestigationEvent.getCreatedAt() != null
                        ? aiInvestigationEvent.getCreatedAt()
                        : LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
    }
}
