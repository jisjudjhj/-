package com.ecommerce.stream;

import com.ecommerce.service.StreamRealtimeRedisSinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@ConditionalOnBean(StreamRealtimeRedisSinkService.class)
@ConditionalOnProperty(prefix = "stream.kafka", name = "enabled", havingValue = "true")
public class StreamDwsKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(StreamDwsKafkaConsumer.class);

    @Autowired
    private StreamRealtimeRedisSinkService streamRealtimeRedisSinkService;

    @KafkaListener(
            topics = "${stream.kafka.user-behavior-topic:dws.user_behavior_distribution}",
            groupId = "${stream.kafka.group-id:ecommerce-stream-dws}",
            containerFactory = "streamKafkaListenerContainerFactory"
    )
    public void consumeUserBehaviorDistribution(String rawJson,
                                                Acknowledgment acknowledgment,
                                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        handle(rawJson, acknowledgment, topic, "userBehaviorDistribution",
                streamRealtimeRedisSinkService::acceptUserBehaviorDistribution);
    }

    @KafkaListener(
            topics = "${stream.kafka.user-category-topic:dws.user_category_preference}",
            groupId = "${stream.kafka.group-id:ecommerce-stream-dws}",
            containerFactory = "streamKafkaListenerContainerFactory"
    )
    public void consumeUserCategoryPreference(String rawJson,
                                              Acknowledgment acknowledgment,
                                              @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        handle(rawJson, acknowledgment, topic, "userCategoryPreference",
                streamRealtimeRedisSinkService::acceptUserCategoryPreference);
    }

    @KafkaListener(
            topics = "${stream.kafka.product-hotness-topic:dws.product_hotness_realtime}",
            groupId = "${stream.kafka.group-id:ecommerce-stream-dws}",
            containerFactory = "streamKafkaListenerContainerFactory"
    )
    public void consumeProductHotness(String rawJson,
                                      Acknowledgment acknowledgment,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        handle(rawJson, acknowledgment, topic, "productHotness",
                streamRealtimeRedisSinkService::acceptProductHotness);
    }

    @KafkaListener(
            topics = "${stream.kafka.recommendation-core-metrics-topic:dws.recommendation_core_metrics_realtime}",
            groupId = "${stream.kafka.group-id:ecommerce-stream-dws}",
            containerFactory = "streamKafkaListenerContainerFactory"
    )
    public void consumeRecommendationCoreMetrics(String rawJson,
                                                 Acknowledgment acknowledgment,
                                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        handle(rawJson, acknowledgment, topic, "recommendationCoreMetrics",
                streamRealtimeRedisSinkService::acceptRecommendationCoreMetrics);
    }

    private void handle(String rawJson,
                        Acknowledgment acknowledgment,
                        String topic,
                        String eventType,
                        Consumer<String> consumer) {
        try {
            consumer.accept(rawJson);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        } catch (Exception exception) {
            log.error("[StreamKafka] consume failed, topic={}, eventType={}, payload={}",
                    topic, eventType, abbreviate(rawJson), exception);
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new IllegalStateException("Kafka stream consume failed: " + eventType, exception);
        }
    }

    private String abbreviate(String rawJson) {
        if (rawJson == null) {
            return "";
        }
        String normalized = rawJson.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }
}
