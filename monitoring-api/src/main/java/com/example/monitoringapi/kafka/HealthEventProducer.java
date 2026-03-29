package com.example.monitoringapi.kafka;

import com.example.monitoringapi.dto.message.HealthEventMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthEventProducer {

    private static final String TOPIC_NAME = "health-events";

    private final KafkaTemplate<String, HealthEventMessage> kafkaTemplate;

    public HealthEventProducer(KafkaTemplate<String, HealthEventMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(HealthEventMessage eventMessage) {
        kafkaTemplate.send(TOPIC_NAME, eventMessage.getMachineIdentifier(), eventMessage);
    }
}