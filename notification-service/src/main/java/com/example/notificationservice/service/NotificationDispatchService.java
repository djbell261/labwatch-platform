package com.example.notificationservice.service;

import com.example.notificationservice.config.NotificationProperties;
import com.example.notificationservice.dto.AlertEventMessage;
import com.example.notificationservice.service.channel.NotificationChannel;
import com.example.notificationservice.service.cooldown.NotificationCooldownStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public NotificationDispatchService(
            List<NotificationChannel> notificationChannels,
            NotificationCooldownStore notificationCooldownStore,
            NotificationProperties notificationProperties,
            Clock clock
    ) {
        this.notificationChannels = notificationChannels;
        this.notificationCooldownStore = notificationCooldownStore;
        this.notificationProperties = notificationProperties;
        this.clock = clock;
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
        Instant lastSentAt = notificationCooldownStore.getLastSentAt(dedupeKey);
        Duration cooldown = Duration.ofSeconds(notificationProperties.getCooldownSeconds());

        if (lastSentAt != null && Duration.between(lastSentAt, now).compareTo(cooldown) < 0) {
            log.info("Skipping duplicate notification due to cooldown");
            return;
        }

        log.info("Dispatching notification for {} alert", severity);

        boolean dispatched = false;
        for (NotificationChannel notificationChannel : notificationChannels) {
            notificationChannel.dispatch(alertEventMessage);
            log.info("Notification dispatched through {}", notificationChannel.channelName());
            dispatched = true;
        }

        if (dispatched) {
            notificationCooldownStore.markSent(dedupeKey, now);
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
