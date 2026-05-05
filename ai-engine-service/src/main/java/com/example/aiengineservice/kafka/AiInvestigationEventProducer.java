package com.example.aiengineservice.kafka;

import com.example.aiengineservice.dto.AiInvestigationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiInvestigationEventProducer {

    private final KafkaTemplate<String, AiInvestigationEvent> kafkaTemplate;
    private final String aiInvestigationEventsTopic;

    public AiInvestigationEventProducer(
            KafkaTemplate<String, AiInvestigationEvent> kafkaTemplate,
            @Value("${app.kafka.topic.ai-investigation-events}") String aiInvestigationEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.aiInvestigationEventsTopic = aiInvestigationEventsTopic;
    }

    public void publish(AiInvestigationEvent aiInvestigationEvent) {
        kafkaTemplate.send(
                aiInvestigationEventsTopic,
                aiInvestigationEvent.getMachineIdentifier(),
                aiInvestigationEvent
        );
    }
}
