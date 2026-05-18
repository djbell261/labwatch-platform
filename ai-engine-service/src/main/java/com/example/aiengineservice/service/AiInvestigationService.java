package com.example.aiengineservice.service;

import com.example.aiengineservice.ai.AiInsightRequest;
import com.example.aiengineservice.ai.AiProvider;
import com.example.aiengineservice.dto.AiInvestigationEvent;
import com.example.aiengineservice.dto.AlertEventMessage;
import com.example.aiengineservice.dto.external.ProcessMetricResponse;
import com.example.aiengineservice.entity.AiInvestigationEntity;
import com.example.aiengineservice.entity.Anomaly;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AiInvestigationService {

    private static final Logger log = LoggerFactory.getLogger(AiInvestigationService.class);
    private static final Set<String> QUALIFYING_SEVERITIES = Set.of("HIGH", "CRITICAL");

    private final AiProvider aiProvider;
    private final AiInsightRequestBuilder aiInsightRequestBuilder;
    private final AiInvestigationContextBuilder contextBuilder;
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
            AiInvestigationEventProducer aiInvestigationEventProducer,
            AiInvestigationRepository aiInvestigationRepository,
            Clock clock
    ) {
        this(
                aiProvider,
                aiInsightRequestBuilder,
                contextBuilder,
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
        try {
            AiInvestigationContextBuilder.AiInvestigationContext context = contextBuilder.build(alertEventMessage);
            AiInsightRequest request = aiInsightRequestBuilder.buildForEvent(
                    formatCreatedAt(alertEventMessage.getCreatedAt()),
                    alertEventMessage.getAlertType(),
                    alertEventMessage.getMetricValue() != null ? alertEventMessage.getMetricValue() : 0.0,
                    "alert-event",
                    null,
                    alertEventMessage.getMachineIdentifier()
            );

            String summary = aiProvider.generateInsight(request);
            AiInvestigationEvent aiInvestigationEvent = new AiInvestigationEvent(
                    UUID.randomUUID(),
                    alertEventMessage.getAlertId(),
                    fallback(alertEventMessage.getMachineIdentifier()),
                    fallback(alertEventMessage.getAlertType()),
                    fallback(alertEventMessage.getSeverity()),
                    summary,
                    deriveLikelyCause(alertEventMessage, context),
                    deriveRecommendedAction(alertEventMessage, context),
                    deriveConfidence(context),
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            );
            log.info("Generated AI investigation");
            return aiInvestigationEvent;
        } catch (Exception exception) {
            log.warn("Failed to generate AI investigation, using fallback", exception);
            return fallbackInvestigation(alertEventMessage);
        }
    }

    private AiInvestigationEvent fallbackInvestigation(AlertEventMessage alertEventMessage) {
        return new AiInvestigationEvent(
                UUID.randomUUID(),
                alertEventMessage != null ? alertEventMessage.getAlertId() : null,
                fallback(alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : null),
                fallback(alertEventMessage != null ? alertEventMessage.getAlertType() : null),
                fallback(alertEventMessage != null ? alertEventMessage.getSeverity() : null),
                "A HIGH/CRITICAL alert was detected for " + fallback(alertEventMessage != null
                        ? alertEventMessage.getMachineIdentifier()
                        : null) + ".",
                "Insufficient AI context available to determine a precise root cause.",
                "Open LabWatch dashboard and inspect recent telemetry/processes for this machine.",
                "LOW",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
    }

    private String deriveLikelyCause(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context
    ) {
        ProcessMetricResponse topProcess = context.topProcess();
        List<Anomaly> anomalies = context.recentAnomalies();
        String trend = context.recentMetricTrend();
        String alertType = normalize(alertEventMessage.getAlertType());

        if ("CPU".equals(alertType) && topProcess != null && topProcess.getCpuPercent() != null) {
            return topProcess.getProcessName() + " is a leading CPU consumer, and " + trend.toLowerCase() + ".";
        }

        if ("MEMORY".equals(alertType) && topProcess != null && topProcess.getMemoryPercent() != null) {
            return topProcess.getProcessName() + " is consuming elevated memory, and " + trend.toLowerCase() + ".";
        }

        if ("DISK".equals(alertType)) {
            return trend + ". Storage pressure may be building from logs, downloads, or persistent artifacts.";
        }

        if (!anomalies.isEmpty()) {
            return "Recent anomalies indicate unusual " + fallback(anomalies.get(0).getEventType()) + " behavior, and " + trend.toLowerCase() + ".";
        }

        return trend + ". The alert likely reflects sustained resource pressure on this machine.";
    }

    private String deriveRecommendedAction(
            AlertEventMessage alertEventMessage,
            AiInvestigationContextBuilder.AiInvestigationContext context
    ) {
        String alertType = normalize(alertEventMessage.getAlertType());
        ProcessMetricResponse topProcess = context.topProcess();

        if ("CPU".equals(alertType)) {
            return topProcess != null && hasText(topProcess.getProcessName())
                    ? "Review process " + topProcess.getProcessName() + " and check for runaway CPU-intensive work."
                    : "Review active processes and background jobs for unexpected CPU spikes.";
        }

        if ("MEMORY".equals(alertType)) {
            return topProcess != null && hasText(topProcess.getProcessName())
                    ? "Review process " + topProcess.getProcessName() + " and close or restart high-memory workloads."
                    : "Review top memory consumers and close or restart unnecessary high-memory apps.";
        }

        if ("DISK".equals(alertType)) {
            return "Inspect disk usage growth, clear stale files or logs, and confirm free space recovers.";
        }

        return "Open LabWatch dashboard and inspect recent telemetry, anomalies, and process activity for this machine.";
    }

    private String deriveConfidence(AiInvestigationContextBuilder.AiInvestigationContext context) {
        boolean hasTrend = context.recentMetricTrend() != null && !context.recentMetricTrend().contains("unavailable");
        boolean hasAnomalies = context.recentAnomalies() != null && !context.recentAnomalies().isEmpty();
        boolean hasTopProcess = context.topProcess() != null && hasText(context.topProcess().getProcessName());

        if (hasTrend && (hasAnomalies || hasTopProcess)) {
            return "HIGH";
        }

        if (hasTrend || hasAnomalies || hasTopProcess) {
            return "MEDIUM";
        }

        return "LOW";
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

    private AiInvestigationEntity toEntity(AiInvestigationEvent aiInvestigationEvent) {
        return new AiInvestigationEntity(
                null,
                aiInvestigationEvent.getInvestigationId() != null ? aiInvestigationEvent.getInvestigationId().toString() : null,
                aiInvestigationEvent.getAlertId() != null ? String.valueOf(aiInvestigationEvent.getAlertId()) : "unknown",
                fallback(aiInvestigationEvent.getMachineIdentifier()),
                fallback(aiInvestigationEvent.getAlertType()),
                fallback(aiInvestigationEvent.getSeverity()),
                fallback(aiInvestigationEvent.getSummary()),
                fallback(aiInvestigationEvent.getLikelyCause()),
                fallback(aiInvestigationEvent.getRecommendedAction()),
                fallback(aiInvestigationEvent.getConfidence()),
                aiInvestigationEvent.getCreatedAt() != null
                        ? aiInvestigationEvent.getCreatedAt()
                        : LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
    }
}
