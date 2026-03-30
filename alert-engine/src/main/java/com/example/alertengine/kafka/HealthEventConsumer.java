package com.example.alertengine.kafka;



import com.example.alertengine.dto.HealthEventMessage;
import com.example.alertengine.service.AlertProcessingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HealthEventConsumer {

    private final AlertProcessingService alertProcessingService;

    public HealthEventConsumer(AlertProcessingService alertProcessingService) {
        this.alertProcessingService = alertProcessingService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.health-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeHealthEvent(HealthEventMessage eventMessage) {
        System.out.println("🔥 RECEIVED EVENT: " + eventMessage);
        alertProcessingService.processHealthEvent(eventMessage);
    }

}