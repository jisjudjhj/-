package com.ecommerce.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;

import org.apache.kafka.common.TopicPartition;

@Configuration
@EnableKafka
public class KafkaStreamConfig {

    @Bean("streamDeadLetterPublishingRecoverer")
    @ConditionalOnProperty(prefix = "stream.kafka", name = "enabled", havingValue = "true")
    public DeadLetterPublishingRecoverer streamDeadLetterPublishingRecoverer(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
                (consumerRecord, exception) -> new TopicPartition(consumerRecord.topic() + ".dlt", consumerRecord.partition()));
    }

    @Bean("streamKafkaErrorHandler")
    @ConditionalOnProperty(prefix = "stream.kafka", name = "enabled", havingValue = "true")
    public DefaultErrorHandler streamKafkaErrorHandler(DeadLetterPublishingRecoverer streamDeadLetterPublishingRecoverer) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(streamDeadLetterPublishingRecoverer, new FixedBackOff(1000L, 2L));
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    @Bean("streamKafkaListenerContainerFactory")
    @ConditionalOnProperty(prefix = "stream.kafka", name = "enabled", havingValue = "true")
    public ConcurrentKafkaListenerContainerFactory<String, String> streamKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler streamKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(streamKafkaErrorHandler);
        return factory;
    }
}
