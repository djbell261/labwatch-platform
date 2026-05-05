package com.example.notificationservice.service.channel;

import com.example.notificationservice.config.NotificationProperties;
import com.example.notificationservice.dto.AlertEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "labwatch.notifications.email",
        name = "enabled",
        havingValue = "true"
)
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final NotificationProperties notificationProperties;

    public EmailNotificationChannel(NotificationProperties notificationProperties) {
        this.notificationProperties = notificationProperties;
    }

    @Override
    public void dispatch(AlertEventMessage alertEventMessage) {
        log.info(
                "Email notification placeholder for {} alert to {} for machine {}",
                alertEventMessage.getSeverity(),
                notificationProperties.getEmail().getTo(),
                alertEventMessage.getMachineIdentifier()
        );
    }

    @Override
    public String channelName() {
        return "EmailNotificationChannel";
    }
}
