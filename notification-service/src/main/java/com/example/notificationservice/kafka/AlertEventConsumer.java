package com.example.notificationservice.kafka;

import com.example.notificationservice.dto.AlertEventMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "alertEventKafkaListenerContainerFactory"
    )
    public void consumeAlertEvent(ConsumerRecord<String, AlertEventMessage> record) {
        AlertEventMessage alertEventMessage = record.value();
        if (alertEventMessage == null) {
            throw new IllegalArgumentException("Kafka record did not contain an alert event payload");
        }

        log.info(
                "event=alert_event_consumed topic={} partition={} offset={} machineIdentifier={} severity={} status={}",
                record.topic(),
                record.partition(),
                record.offset(),
                alertEventMessage.getMachineIdentifier(),
                alertEventMessage.getSeverity(),
                alertEventMessage.getStatus()
        );
        notificationDispatchService.dispatchAlertNotification(alertEventMessage);
    }
}
