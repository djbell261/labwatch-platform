package com.example.monitoringapi.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topic.health-events}")
    private String topicName;

    @Bean
    @ConditionalOnProperty(
            name = "app.kafka.topic.auto-create-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public NewTopic healthEventsTopic() {
        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
