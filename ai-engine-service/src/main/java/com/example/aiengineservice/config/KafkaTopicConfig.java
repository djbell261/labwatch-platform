package com.example.aiengineservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topic.anomaly-events}")
    private String anomalyEventsTopicName;

    @Value("${app.kafka.topic.ai-investigation-events}")
    private String aiInvestigationEventsTopicName;

    @Value("${app.kafka.topic.health-events-dlt:health-events.ai-engine.dlt}")
    private String healthEventsDltTopicName;

    @Value("${app.kafka.topic.alert-events-dlt:alert-events.ai-engine.dlt}")
    private String alertEventsDltTopicName;

    @Bean
    @ConditionalOnProperty(
            name = "app.kafka.topic.auto-create-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public NewTopic anomalyEventsTopic() {
        return TopicBuilder.name(anomalyEventsTopicName)
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
    public NewTopic aiInvestigationEventsTopic() {
        return TopicBuilder.name(aiInvestigationEventsTopicName)
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

    @Bean
    @ConditionalOnProperty(
            name = "app.kafka.topic.auto-create-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public NewTopic alertEventsDltTopic() {
        return TopicBuilder.name(alertEventsDltTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
