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
            throw new IllegalArgumentException("Kafka record did not contain an alert event payload");
        }

        log.info(
                "event=alert_event_consumed topic={} partition={} offset={} machineIdentifier={} severity={} status={}",
                record.topic(),
                record.partition(),
                record.offset(),
                alertEventMessage.getMachineIdentifier(),
                alertEventMessage.getSeverity(),
                alertEventMessage.getStatus()
        );
        aiInvestigationService.processAlertEvent(alertEventMessage);
    }
}
