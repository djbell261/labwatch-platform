package com.example.alertengine.config;

import com.example.alertengine.dto.HealthEventMessage;
import com.example.alertengine.dto.AlertEventMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${app.kafka.retry.attempts:3}")
    private long retryAttempts;

    @Value("${app.kafka.retry.backoff-ms:1000}")
    private long retryBackoffMs;

    @Value("${app.kafka.topic.health-events-dlt:health-events.alert-engine.dlt}")
    private String healthEventsDltTopic;

    @Bean
    public ConsumerFactory<String, HealthEventMessage> healthEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, HealthEventMessage.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ProducerFactory<String, Object> dltProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public ProducerFactory<String, AlertEventMessage> alertEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate() {
        return new KafkaTemplate<>(dltProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, AlertEventMessage> kafkaTemplate() {
        return new KafkaTemplate<>(alertEventProducerFactory());
    }

    @Bean
    public CommonErrorHandler alertEngineKafkaErrorHandler(KafkaTemplate<String, Object> dltKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, exception) -> new TopicPartition(healthEventsDltTopic, record.partition())
        );
        FixedBackOff fixedBackOff = new FixedBackOff(retryBackoffMs, Math.max(0, retryAttempts - 1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, fixedBackOff);
        errorHandler.setRetryListeners(new LoggingRetryListener());
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, HealthEventMessage> healthEventKafkaListenerContainerFactory(
            CommonErrorHandler alertEngineKafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, HealthEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(healthEventConsumerFactory());
        factory.setCommonErrorHandler(alertEngineKafkaErrorHandler);
        return factory;
    }

    private String topic(ConsumerRecord<?, ?> record) {
        return record != null ? record.topic() : "unknown";
    }

    private int partition(ConsumerRecord<?, ?> record) {
        return record != null ? record.partition() : -1;
    }

    private long offset(ConsumerRecord<?, ?> record) {
        return record != null ? record.offset() : -1L;
    }

    private final class LoggingRetryListener implements org.springframework.kafka.listener.RetryListener {

        @Override
        public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
            log.warn(
                    "event=kafka_retry service=alert-engine topic={} partition={} offset={} attempt={} error={}",
                    topic(record),
                    partition(record),
                    offset(record),
                    deliveryAttempt,
                    ex.getClass().getSimpleName()
            );
        }

        @Override
        public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
            log.error(
                    "event=kafka_dlt service=alert-engine topic={} partition={} offset={} dltTopic={} error={}",
                    topic(record),
                    partition(record),
                    offset(record),
                    healthEventsDltTopic,
                    ex.getClass().getSimpleName(),
                    ex
            );
        }
    }
}
