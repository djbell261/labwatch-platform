package com.example.alertengine.kafka;

import com.example.alertengine.dto.AlertEventMessage;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AlertEventProducer.class);

    private final KafkaTemplate<String, AlertEventMessage> kafkaTemplate;
    private final String alertEventsTopic;

    public AlertEventProducer(
            KafkaTemplate<String, AlertEventMessage> kafkaTemplate,
            @Value("${app.kafka.topic.alert-events}") String alertEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.alertEventsTopic = alertEventsTopic;
    }

    public void publish(AlertEventMessage alertEventMessage) {
        log.info(
                "Publishing alert event to {} for machine {} with severity {}",
                alertEventsTopic,
                alertEventMessage.getMachineIdentifier(),
                alertEventMessage.getSeverity()
        );

        try {
            kafkaTemplate.send(
                    alertEventsTopic,
                    alertEventMessage.getMachineIdentifier(),
                    alertEventMessage
            ).whenComplete(this::logPublishResult);
        } catch (Exception exception) {
            log.warn(
                    "Failed to publish alert event for machine {} alert type {}: {}",
                    alertEventMessage.getMachineIdentifier(),
                    alertEventMessage.getAlertType(),
                    exception.getMessage()
            );
        }
    }

    private void logPublishResult(SendResult<String, AlertEventMessage> sendResult, Throwable throwable) {
        if (throwable != null) {
            log.warn("Failed to publish alert event", throwable);
            return;
        }

        RecordMetadata metadata = sendResult.getRecordMetadata();
        log.info(
                "Successfully published alert event to {} partition {} offset {}",
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
        );
    }
}
