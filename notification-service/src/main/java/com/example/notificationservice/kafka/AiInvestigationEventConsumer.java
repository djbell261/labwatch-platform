package com.example.notificationservice.kafka;

import com.example.notificationservice.dto.AiInvestigationEvent;
import com.example.notificationservice.service.AiInvestigationNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AiInvestigationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiInvestigationEventConsumer.class);

    private final AiInvestigationNotificationService aiInvestigationNotificationService;

    public AiInvestigationEventConsumer(AiInvestigationNotificationService aiInvestigationNotificationService) {
        this.aiInvestigationNotificationService = aiInvestigationNotificationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.ai-investigation-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "aiInvestigationKafkaListenerContainerFactory"
    )
    public void consumeAiInvestigationEvent(ConsumerRecord<String, AiInvestigationEvent> record) {
        AiInvestigationEvent aiInvestigationEvent = record.value();
        if (aiInvestigationEvent == null) {
            throw new IllegalArgumentException("Kafka record did not contain an AI investigation payload");
        }
        log.info(
                "event=ai_investigation_event_consumed topic={} partition={} offset={} machineIdentifier={} investigationId={}",
                record.topic(),
                record.partition(),
                record.offset(),
                aiInvestigationEvent.getMachineIdentifier(),
                aiInvestigationEvent.getInvestigationId()
        );
        aiInvestigationNotificationService.logInvestigation(aiInvestigationEvent);
    }
}
