package com.example.aiengineservice.kafka;

import com.example.aiengineservice.dto.AnomalyEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnomalyEventProducer {

    private final KafkaTemplate<String, AnomalyEventMessage> kafkaTemplate;
    private final String anomalyEventsTopic;

    public AnomalyEventProducer(
            KafkaTemplate<String, AnomalyEventMessage> kafkaTemplate,
            @Value("${app.kafka.topic.anomaly-events}") String anomalyEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.anomalyEventsTopic = anomalyEventsTopic;
    }

    public void publish(AnomalyEventMessage anomalyEventMessage) {
        kafkaTemplate.send(
                anomalyEventsTopic,
                String.valueOf(anomalyEventMessage.getMachineId()),
                anomalyEventMessage
        );
    }
}
