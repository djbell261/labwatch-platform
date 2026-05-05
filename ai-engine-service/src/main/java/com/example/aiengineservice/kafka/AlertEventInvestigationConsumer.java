package com.example.aiengineservice.kafka;

import com.example.aiengineservice.dto.AlertEventMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.example.aiengineservice.service.AiInvestigationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertEventInvestigationConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertEventInvestigationConsumer.class);

    private final AiInvestigationService aiInvestigationService;

    public AlertEventInvestigationConsumer(AiInvestigationService aiInvestigationService) {
        this.aiInvestigationService = aiInvestigationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.alert-events}",
            groupId = "${app.kafka.consumer.group.ai-investigation}",
            containerFactory = "alertEventKafkaListenerContainerFactory"
    )
    public void consumeAlertEvent(ConsumerRecord<String, AlertEventMessage> record) {
        AlertEventMessage alertEventMessage = record.value();
        if (alertEventMessage == null) {
            log.info("Skipping alert event because it does not qualify for AI investigation");
            return;
        }

        aiInvestigationService.processAlertEvent(alertEventMessage);
    }
}
