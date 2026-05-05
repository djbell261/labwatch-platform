package com.example.notificationservice.kafka;

import com.example.notificationservice.dto.AlertEventMessage;
import com.example.notificationservice.service.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertEventConsumer.class);

    private final NotificationDispatchService notificationDispatchService;

    public AlertEventConsumer(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.alert-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeAlertEvent(AlertEventMessage alertEventMessage) {
        try {
            log.info(
                    "Received alert event from Kafka for machine {} severity {} status {}",
                    alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : "unknown",
                    alertEventMessage != null ? alertEventMessage.getSeverity() : "unknown",
                    alertEventMessage != null ? alertEventMessage.getStatus() : "unknown"
            );

            notificationDispatchService.dispatchAlertNotification(alertEventMessage);
        } catch (Exception exception) {
            log.warn(
                    "Failed to process alert event for machine {} alert type {}: {}",
                    alertEventMessage != null ? alertEventMessage.getMachineIdentifier() : "unknown",
                    alertEventMessage != null ? alertEventMessage.getAlertType() : "unknown",
                    exception.getMessage()
            );
        }
    }

}
