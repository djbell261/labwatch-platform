package com.example.monitoringapi.kafka;

import com.example.monitoringapi.dto.message.HealthEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthEventProducer {

    private final KafkaTemplate<String, HealthEventMessage> kafkaTemplate;
    private final String topicName;

    public HealthEventProducer(
            KafkaTemplate<String, HealthEventMessage> kafkaTemplate,
            @Value("${app.kafka.topic.health-events}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void sendEvent(HealthEventMessage eventMessage) {
        kafkaTemplate.send(topicName, eventMessage.getMachineIdentifier(), eventMessage);
    }
}
