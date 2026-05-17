package com.example.aiengineservice.kafka;

import com.example.aiengineservice.dto.AlertEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnomalyPromotedAlertProducer {

    private final KafkaTemplate<String, AlertEventMessage> kafkaTemplate;
    private final String alertEventsTopic;

    public AnomalyPromotedAlertProducer(
            KafkaTemplate<String, AlertEventMessage> kafkaTemplate,
            @Value("${app.kafka.topic.alert-events}") String alertEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.alertEventsTopic = alertEventsTopic;
    }

    public void publish(AlertEventMessage alertEventMessage) {
        kafkaTemplate.send(
                alertEventsTopic,
                String.join("|", alertEventMessage.getMachineIdentifier(), alertEventMessage.getAlertType()),
                alertEventMessage
        );
    }
}
