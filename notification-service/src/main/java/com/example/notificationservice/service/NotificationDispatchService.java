package com.example.notificationservice.service;

import com.example.notificationservice.config.NotificationProperties;
import com.example.notificationservice.dto.AlertEventMessage;
import com.example.notificationservice.service.channel.NotificationChannel;
import com.example.notificationservice.service.cooldown.NotificationCooldownStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final Set<String> NOTIFIABLE_SEVERITIES = Set.of("HIGH", "CRITICAL");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    private final List<NotificationChannel> notificationChannels;
    private final NotificationCooldownStore notificationCooldownStore;
    private final NotificationProperties notificationProperties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Autowired
    public NotificationDispatchService(
            List<NotificationChannel> notificationChannels,
            NotificationCooldownStore notificationCooldownStore,
            NotificationProperties notificationProperties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.notificationChannels = notificationChannels;
        this.notificationCooldownStore = notificationCooldownStore;
        this.notificationProperties = notificationProperties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    NotificationDispatchService(
            List<? extends NotificationChannel> notificationChannels,
            NotificationCooldownStore notificationCooldownStore,
            NotificationProperties notificationProperties,
            Clock clock
    ) {
        this(
                List.copyOf(notificationChannels),
                notificationCooldownStore,
                notificationProperties,
                clock,
                new SimpleMeterRegistry()
        );
    }

    public void dispatchAlertNotification(AlertEventMessage alertEventMessage) {
        if (alertEventMessage == null) {
            log.info("Skipping notification because severity/status does not qualify");
            return;
        }

        String status = normalize(alertEventMessage.getStatus());
        String severity = normalize(alertEventMessage.getSeverity());

        if (!"ACTIVE".equals(status) || !NOTIFIABLE_SEVERITIES.contains(severity)) {
            log.info("Skipping notification because severity/status does not qualify");
            return;
        }

        String dedupeKey = dedupeKey(alertEventMessage, severity);
        Instant now = clock.instant();
        Duration cooldown = Duration.ofSeconds(notificationProperties.getCooldownSeconds());

        if (!notificationCooldownStore.tryAcquire(dedupeKey, now, cooldown)) {
            log.info("event=notification_skipped reason=cooldown dedupeKey={}", dedupeKey);
            return;
        }

        log.info("event=notification_dispatch_started severity={} dedupeKey={}", severity, dedupeKey);

        boolean deliveredAtLeastOnce = false;
        for (NotificationChannel notificationChannel : notificationChannels) {
            try {
                boolean delivered = notificationChannel.dispatchWithResult(alertEventMessage);
                if (delivered) {
                    deliveredAtLeastOnce = true;
                    meterRegistry.counter("labwatch.notifications.sent", "channel", notificationChannel.channelName()).increment();
                    log.info(
                            "event=notification_dispatched channel={} machineIdentifier={} alertType={}",
                            notificationChannel.channelName(),
                            alertEventMessage.getMachineIdentifier(),
                            alertEventMessage.getAlertType()
                    );
                } else {
                    log.info(
                            "event=notification_channel_skipped channel={} machineIdentifier={} alertType={}",
                            notificationChannel.channelName(),
                            alertEventMessage.getMachineIdentifier(),
                            alertEventMessage.getAlertType()
                    );
                }
            } catch (Exception exception) {
                meterRegistry.counter("labwatch.notifications.failed", "channel", notificationChannel.channelName()).increment();
                log.error(
                        "event=notification_failed channel={} machineIdentifier={} alertType={} error={}",
                        notificationChannel.channelName(),
                        alertEventMessage.getMachineIdentifier(),
                        alertEventMessage.getAlertType(),
                        exception.getClass().getSimpleName(),
                        exception
                );
            }
        }

        if (!deliveredAtLeastOnce) {
            notificationCooldownStore.release(dedupeKey);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    public static String fallback(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public static String formatAlertTime(AlertEventMessage alertEventMessage) {
        return alertEventMessage.getCreatedAt() != null
                ? alertEventMessage.getCreatedAt().format(TIME_FORMATTER)
                : "unknown";
    }

    public static String formatMetricValue(AlertEventMessage alertEventMessage) {
        return alertEventMessage.getMetricValue() != null
                ? String.format("%.1f%%", alertEventMessage.getMetricValue())
                : "unknown";
    }

    private String dedupeKey(AlertEventMessage alertEventMessage, String severity) {
        return String.join(
                "|",
                normalize(alertEventMessage.getMachineIdentifier()),
                normalize(alertEventMessage.getAlertType()),
                severity
        );
    }
}
