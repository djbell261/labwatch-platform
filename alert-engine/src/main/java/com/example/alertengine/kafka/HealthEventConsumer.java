package com.example.alertengine.kafka;

import com.example.alertengine.dto.HealthEventMessage;
import com.example.alertengine.service.AlertProcessingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HealthEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(HealthEventConsumer.class);

    private final AlertProcessingService alertProcessingService;
    private final MeterRegistry meterRegistry;

    public HealthEventConsumer(AlertProcessingService alertProcessingService, MeterRegistry meterRegistry) {
        this.alertProcessingService = alertProcessingService;
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

        meterRegistry.counter("labwatch.health.events.consumed", "consumer", "alert-engine").increment();
        log.info(
                "event=health_event_consumed topic={} partition={} offset={} machineIdentifier={} eventType={}",
                record.topic(),
                record.partition(),
                record.offset(),
                eventMessage.getMachineIdentifier(),
                eventMessage.getEventType()
        );
        alertProcessingService.processHealthEvent(eventMessage);
    }
}
