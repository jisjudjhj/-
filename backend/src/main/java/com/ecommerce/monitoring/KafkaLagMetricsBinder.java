package com.ecommerce.monitoring;

import com.ecommerce.config.StreamKafkaConsumerProperties;
import com.ecommerce.service.StreamKafkaMonitorService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class KafkaLagMetricsBinder {

    private final AtomicLong consumerLag = new AtomicLong(0L);
    private final AtomicLong deadLetterMessages = new AtomicLong(0L);

    @Autowired(required = false)
    private StreamKafkaMonitorService streamKafkaMonitorService;

    @Autowired
    private StreamKafkaConsumerProperties streamKafkaConsumerProperties;

    public KafkaLagMetricsBinder(MeterRegistry meterRegistry) {
        Gauge.builder("ecommerce.kafka.consumer.lag.total", consumerLag, AtomicLong::get)
                .description("Total consumer lag of core Kafka topics")
                .register(meterRegistry);
        Gauge.builder("ecommerce.kafka.dead_letter.messages.total", deadLetterMessages, AtomicLong::get)
                .description("Total dead-letter messages of core Kafka topics")
                .register(meterRegistry);
    }

    @Scheduled(initialDelay = 15000L, fixedDelayString = "${performance.metrics.kafka-lag-refresh-ms:15000}")
    public void refreshMetrics() {
        if (!streamKafkaConsumerProperties.isEnabled() || streamKafkaMonitorService == null) {
            consumerLag.set(0L);
            deadLetterMessages.set(0L);
            return;
        }

        Map<String, Object> monitor = streamKafkaMonitorService.getRealtimeMonitor(
                streamKafkaConsumerProperties.getGroupId(),
                collectCoreTopics());

        Map<String, Object> consumerLagData = safeMap(monitor.get("consumerLag"));
        Map<String, Object> deadLetterData = safeMap(monitor.get("deadLetter"));
        consumerLag.set(parseLong(consumerLagData.get("totalLag")));
        deadLetterMessages.set(parseLong(deadLetterData.get("totalMessages")));
    }

    private List<String> collectCoreTopics() {
        List<String> topics = new ArrayList<>();
        addTopic(topics, streamKafkaConsumerProperties.getUserBehaviorTopic());
        addTopic(topics, streamKafkaConsumerProperties.getUserCategoryTopic());
        addTopic(topics, streamKafkaConsumerProperties.getProductHotnessTopic());
        addTopic(topics, streamKafkaConsumerProperties.getProductChangedTopic());
        addTopic(topics, streamKafkaConsumerProperties.getProductCdcTopic());
        return topics;
    }

    private void addTopic(List<String> topics, String topic) {
        if (topics == null || !StringUtils.hasText(topic)) {
            return;
        }
        String normalized = topic.trim();
        if (!normalized.isEmpty() && !topics.contains(normalized)) {
            topics.add(normalized);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return java.util.Collections.emptyMap();
    }

    private long parseLong(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (Exception ignore) {
            return 0L;
        }
    }
}
