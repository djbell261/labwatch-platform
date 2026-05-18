package com.example.aiengineservice.kafka;

import com.example.aiengineservice.dto.HealthEventMessage;
import com.example.aiengineservice.service.AnomalyDetectionService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HealthEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(HealthEventConsumer.class);

    private final AnomalyDetectionService anomalyDetectionService;
    private final MeterRegistry meterRegistry;

    public HealthEventConsumer(AnomalyDetectionService anomalyDetectionService, MeterRegistry meterRegistry) {
        this.anomalyDetectionService = anomalyDetectionService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.health-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "healthEventKafkaListenerContainerFactory"
    )
    public void consumeHealthEvent(ConsumerRecord<String, HealthEventMessage> record) {
        HealthEventMessage eventMessage = record.value();
        if (eventMessage == null) {
            throw new IllegalArgumentException("Kafka record did not contain a health event payload");
        }

        meterRegistry.counter("labwatch.health.events.consumed", "consumer", "ai-engine-service").increment();
        log.info(
                "event=health_event_consumed topic={} partition={} offset={} machineIdentifier={} eventType={}",
                record.topic(),
                record.partition(),
                record.offset(),
                eventMessage.getMachineIdentifier(),
                eventMessage.getEventType()
        );
        anomalyDetectionService.processHealthEvent(eventMessage);
    }
}
