package com.example.aiengineservice.kafka;

import com.example.aiengineservice.dto.HealthEventMessage;
import com.example.aiengineservice.service.AnomalyDetectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HealthEventConsumer {

    private final AnomalyDetectionService anomalyDetectionService;

    public HealthEventConsumer(AnomalyDetectionService anomalyDetectionService) {
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.health-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeHealthEvent(HealthEventMessage eventMessage) {
        anomalyDetectionService.processHealthEvent(eventMessage);
    }
}
