package com.example.notificationservice.service.channel;

import com.example.notificationservice.config.NotificationProperties;
import com.example.notificationservice.dto.AlertEventMessage;
import com.example.notificationservice.service.NotificationDispatchService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final NotificationProperties notificationProperties;
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public EmailNotificationChannel(
            NotificationProperties notificationProperties,
            JavaMailSender mailSender,
            MailProperties mailProperties
    ) {
        this.notificationProperties = notificationProperties;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @PostConstruct
    void logStartupState() {
        if (!notificationProperties.getEmail().isEnabled()) {
            log.info("Email notifications disabled");
        }
    }

    @Override
    public void dispatch(AlertEventMessage alertEventMessage) {
        if (!notificationProperties.getEmail().isEnabled()) {
            log.info("Email notifications disabled");
            return;
        }

        String recipient = notificationProperties.getEmail().getTo();
        if (isBlank(recipient)) {
            log.warn("Email notifications enabled but no recipient configured; skipping email");
            return;
        }

        if (isBlank(mailProperties.getHost())) {
            log.warn("Email notifications enabled but SMTP host is missing; skipping email");
            return;
        }

        try {
            log.info("Sending email notification to {}", recipient);
            mailSender.send(buildMessage(alertEventMessage, recipient));
            log.info("Email notification sent");
        } catch (Exception exception) {
            log.error("Email notification failed", exception);
        }
    }

    @Override
    public String channelName() {
        return "EmailNotificationChannel";
    }

    private SimpleMailMessage buildMessage(AlertEventMessage alertEventMessage, String recipient) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setFrom(notificationProperties.getEmail().getFrom());
        message.setSubject(formatSubject(alertEventMessage));
        message.setText(formatBody(alertEventMessage));
        return message;
    }

    private String formatSubject(AlertEventMessage alertEventMessage) {
        return String.format(
                "[%s] %s Alert on %s",
                NotificationDispatchService.fallback(alertEventMessage.getSeverity()),
                NotificationDispatchService.fallback(alertEventMessage.getAlertType()),
                NotificationDispatchService.fallback(alertEventMessage.getMachineIdentifier())
        );
    }

    private String formatBody(AlertEventMessage alertEventMessage) {
        return String.join(
                "\n",
                "LabWatch Alert Notification",
                "",
                "Machine: " + NotificationDispatchService.fallback(alertEventMessage.getMachineIdentifier()),
                "Hostname: " + NotificationDispatchService.fallback(alertEventMessage.getHostname()),
                "Alert Type: " + NotificationDispatchService.fallback(alertEventMessage.getAlertType()),
                "Severity: " + NotificationDispatchService.fallback(alertEventMessage.getSeverity()),
                "Status: " + NotificationDispatchService.fallback(alertEventMessage.getStatus()),
                "Metric Value: " + NotificationDispatchService.formatMetricValue(alertEventMessage),
                "Created At: " + formatCreatedAt(alertEventMessage),
                "",
                "Recommended next step:",
                "Open LabWatch and investigate this machine."
        );
    }

    private String formatCreatedAt(AlertEventMessage alertEventMessage) {
        return alertEventMessage.getCreatedAt() == null
                ? "unknown"
                : alertEventMessage.getCreatedAt().atOffset(java.time.ZoneOffset.UTC).toInstant().toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
