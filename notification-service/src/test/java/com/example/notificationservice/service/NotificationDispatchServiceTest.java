package com.example.notificationservice.service;

import com.example.notificationservice.config.NotificationProperties;
import com.example.notificationservice.dto.AlertEventMessage;
import com.example.notificationservice.service.channel.EmailNotificationChannel;
import com.example.notificationservice.service.channel.NotificationChannel;
import com.example.notificationservice.service.cooldown.InMemoryNotificationCooldownStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationDispatchServiceTest {

    private MutableClock clock;
    private RecordingNotificationChannel notificationChannel;
    private NotificationDispatchService notificationDispatchService;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-05-05T13:00:00Z"), ZoneOffset.UTC);
        notificationChannel = new RecordingNotificationChannel();

        notificationDispatchService = new NotificationDispatchService(
                List.of(notificationChannel),
                new InMemoryNotificationCooldownStore(),
                notificationProperties(),
                clock
        );
    }

    @Test
    void highAlertDispatchesNotification() {
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "HIGH", "ACTIVE"));

        assertEquals(1, notificationChannel.dispatchedAlerts.size());
    }

    @Test
    void criticalAlertDispatchesNotification() {
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "DISK", "CRITICAL", "ACTIVE"));

        assertEquals(1, notificationChannel.dispatchedAlerts.size());
    }

    @Test
    void lowAndMediumAlertsAreSkipped() {
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "LOW", "ACTIVE"));
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "MEDIUM", "ACTIVE"));

        assertEquals(0, notificationChannel.dispatchedAlerts.size());
    }

    @Test
    void resolvedAlertIsSkipped() {
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "HIGH", "RESOLVED"));

        assertEquals(0, notificationChannel.dispatchedAlerts.size());
    }

    @Test
    void duplicateAlertWithinCooldownIsSkipped() {
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "HIGH", "ACTIVE"));
        clock.advanceSeconds(120);

        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "HIGH", "ACTIVE"));

        assertEquals(1, notificationChannel.dispatchedAlerts.size());
    }

    @Test
    void sameAlertAfterCooldownIsAllowed() {
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "HIGH", "ACTIVE"));
        clock.advanceSeconds(301);

        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "HIGH", "ACTIVE"));

        assertEquals(2, notificationChannel.dispatchedAlerts.size());
    }

    @Test
    void differentMachineTypeOrSeverityDoesNotReuseCooldownKey() {
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "HIGH", "ACTIVE"));
        clock.advanceSeconds(60);

        notificationDispatchService.dispatchAlertNotification(alert("machine-b", "CPU", "HIGH", "ACTIVE"));
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "MEMORY", "HIGH", "ACTIVE"));
        notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "CRITICAL", "ACTIVE"));

        assertEquals(4, notificationChannel.dispatchedAlerts.size());
    }

    @Test
    void consoleChannelStillDispatchesWhenEmailFails() {
        NotificationProperties notificationProperties = notificationProperties();
        notificationProperties.getEmail().setEnabled(true);
        notificationProperties.getEmail().setTo("alerts@example.com");
        notificationProperties.getEmail().setFrom("labwatch@localhost");

        MailProperties mailProperties = new MailProperties();
        mailProperties.setHost("smtp.example.com");

        RecordingNotificationChannel consoleChannel = new RecordingNotificationChannel();
        NotificationChannel emailChannel = new EmailNotificationChannel(
                notificationProperties,
                new FailingMailSender(),
                mailProperties
        );

        notificationDispatchService = new NotificationDispatchService(
                List.of(emailChannel, consoleChannel),
                new InMemoryNotificationCooldownStore(),
                notificationProperties,
                clock
        );

        assertDoesNotThrow(() ->
                notificationDispatchService.dispatchAlertNotification(alert("machine-a", "CPU", "HIGH", "ACTIVE"))
        );
        assertEquals(1, consoleChannel.dispatchedAlerts.size());
    }

    private NotificationProperties notificationProperties() {
        NotificationProperties notificationProperties = new NotificationProperties();
        notificationProperties.setCooldownSeconds(300);
        return notificationProperties;
    }

    private AlertEventMessage alert(String machineIdentifier, String alertType, String severity, String status) {
        return new AlertEventMessage(
                1L,
                machineIdentifier,
                machineIdentifier + ".local",
                alertType,
                severity,
                status,
                95.0,
                null,
                null,
                null,
                LocalDateTime.ofInstant(clock.instant(), clock.getZone())
        );
    }

    private static final class RecordingNotificationChannel implements NotificationChannel {

        private final List<AlertEventMessage> dispatchedAlerts = new ArrayList<>();

        @Override
        public void dispatch(AlertEventMessage alertEventMessage) {
            dispatchedAlerts.add(alertEventMessage);
        }

        @Override
        public String channelName() {
            return "RecordingNotificationChannel";
        }
    }

    private static final class FailingMailSender implements JavaMailSender {

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            throw new MailSendException("smtp failure");
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            throw new MailSendException("smtp failure");
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage() {
            return null;
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage(InputStream contentStream) {
            return null;
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage mimeMessage) {
            throw new MailSendException("smtp failure");
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage... mimeMessages) {
            throw new MailSendException("smtp failure");
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator mimeMessagePreparator) {
            throw new MailSendException("smtp failure");
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator... mimeMessagePreparators) {
            throw new MailSendException("smtp failure");
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zoneId;

        private MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
