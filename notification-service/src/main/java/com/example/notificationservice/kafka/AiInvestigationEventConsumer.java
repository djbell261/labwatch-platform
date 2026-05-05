package com.example.notificationservice.kafka;

import com.example.notificationservice.dto.AiInvestigationEvent;
import com.example.notificationservice.service.AiInvestigationNotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AiInvestigationEventConsumer {

    private final AiInvestigationNotificationService aiInvestigationNotificationService;

    public AiInvestigationEventConsumer(AiInvestigationNotificationService aiInvestigationNotificationService) {
        this.aiInvestigationNotificationService = aiInvestigationNotificationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.ai-investigation-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "aiInvestigationKafkaListenerContainerFactory"
    )
    public void consumeAiInvestigationEvent(AiInvestigationEvent aiInvestigationEvent) {
        aiInvestigationNotificationService.logInvestigation(aiInvestigationEvent);
    }
}
