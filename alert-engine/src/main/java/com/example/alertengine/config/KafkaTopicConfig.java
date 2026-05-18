package com.example.alertengine.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topic.alert-events}")
    private String alertEventsTopicName;

    @Value("${app.kafka.topic.health-events-dlt:health-events.alert-engine.dlt}")
    private String healthEventsDltTopicName;

    @Bean
    @ConditionalOnProperty(
            name = "app.kafka.topic.auto-create-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public NewTopic alertEventsTopic() {
        return TopicBuilder.name(alertEventsTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.kafka.topic.auto-create-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public NewTopic healthEventsDltTopic() {
        return TopicBuilder.name(healthEventsDltTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
