package com.ecommerce.service.impl;

import com.ecommerce.config.StreamKafkaConsumerProperties;
import com.ecommerce.service.StreamKafkaMonitorService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(prefix = "stream.kafka", name = "enabled", havingValue = "true")
public class StreamKafkaMonitorServiceImpl implements StreamKafkaMonitorService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MIN_MONITOR_CACHE_MILLIS = 1000L;

    private final Map<String, MonitorCacheEntry> monitorCache = new ConcurrentHashMap<>();
    private final Object monitorCacheLock = new Object();

    @Value("${spring.kafka.bootstrap-servers:}")
    private String bootstrapServers;

    @Value("${performance.metrics.stream-monitor-cache-ms:5000}")
    private long monitorCacheMillis;

    @Autowired
    private StreamKafkaConsumerProperties streamKafkaConsumerProperties;

    @Override
    public Map<String, Object> getRealtimeMonitor(String consumerGroupId, Collection<String> topics) {
        Set<String> dataTopics = collectDataTopics(topics);
        String cacheKey = buildMonitorCacheKey(consumerGroupId, dataTopics);
        long now = System.currentTimeMillis();
        MonitorCacheEntry cached = monitorCache.get(cacheKey);
        if (cached != null && cached.expireAtMillis > now) {
            return shallowCopy(cached.payload);
        }

        synchronized (monitorCacheLock) {
            cached = monitorCache.get(cacheKey);
            if (cached != null && cached.expireAtMillis > now) {
                return shallowCopy(cached.payload);
            }

            Map<String, Object> payload = computeMonitorPayload(consumerGroupId, dataTopics);
            monitorCache.put(cacheKey, new MonitorCacheEntry(
                    payload,
                    now + Math.max(monitorCacheMillis, MIN_MONITOR_CACHE_MILLIS))
            );
            if (monitorCache.size() > 64) {
                monitorCache.clear();
                monitorCache.put(cacheKey, new MonitorCacheEntry(
                        payload,
                        now + Math.max(monitorCacheMillis, MIN_MONITOR_CACHE_MILLIS))
                );
            }
            return shallowCopy(payload);
        }
    }

    private Map<String, Object> computeMonitorPayload(String consumerGroupId, Set<String> dataTopics) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("updatedAt", nowText());
        payload.put("bootstrapServers", bootstrapServers);

        if (!StringUtils.hasText(bootstrapServers)) {
            payload.put("available", false);
            payload.put("consumerLag", emptyConsumerLag(consumerGroupId));
            payload.put("deadLetter", emptyDeadLetter());
            payload.put("alerts", Collections.emptyList());
            payload.put("error", "Kafka bootstrap servers 未配置");
            return payload;
        }

        try (AdminClient adminClient = createAdminClient()) {
            Map<String, Object> consumerLag = inspectConsumerLag(adminClient, consumerGroupId, dataTopics);
            Map<String, Object> deadLetter = inspectDeadLetter(adminClient, dataTopics);

            payload.put("available", true);
            payload.put("consumerLag", consumerLag);
            payload.put("deadLetter", deadLetter);
            payload.put("alerts", buildAlerts(consumerLag, deadLetter));
            return payload;
        } catch (Exception exception) {
            payload.put("available", false);
            payload.put("consumerLag", emptyConsumerLag(consumerGroupId));
            payload.put("deadLetter", emptyDeadLetter());
            payload.put("alerts", Collections.emptyList());
            payload.put("error", exception.getMessage());
            return payload;
        }
    }

    private Set<String> collectDataTopics(Collection<String> topics) {
        Set<String> dataTopics = new LinkedHashSet<>();
        if (topics == null) {
            return dataTopics;
        }
        for (String topic : topics) {
            if (StringUtils.hasText(topic)) {
                dataTopics.add(topic.trim());
            }
        }
        return dataTopics;
    }

    private String buildMonitorCacheKey(String consumerGroupId, Set<String> dataTopics) {
        StringBuilder key = new StringBuilder();
        key.append(StringUtils.hasText(consumerGroupId) ? consumerGroupId.trim() : "default");
        key.append("|");
        if (dataTopics == null || dataTopics.isEmpty()) {
            key.append("none");
        } else {
            for (String topic : dataTopics) {
                key.append(topic).append(",");
            }
        }
        return key.toString();
    }

    private Map<String, Object> shallowCopy(Map<String, Object> payload) {
        return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    private AdminClient createAdminClient() {
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");
        return AdminClient.create(config);
    }

    private Map<String, Object> inspectConsumerLag(AdminClient adminClient,
                                                   String consumerGroupId,
                                                   Set<String> dataTopics) throws Exception {
        Map<String, Object> result = emptyConsumerLag(consumerGroupId);
        if (!StringUtils.hasText(consumerGroupId) || dataTopics.isEmpty()) {
            return result;
        }

        Map<TopicPartition, OffsetAndMetadata> committedOffsets;
        try {
            committedOffsets = adminClient.listConsumerGroupOffsets(consumerGroupId)
                    .partitionsToOffsetAndMetadata()
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            if (isMissingConsumerGroup(exception)) {
                result.put("groupFound", false);
                return result;
            }
            throw exception;
        }

        if (committedOffsets == null || committedOffsets.isEmpty()) {
            result.put("groupFound", true);
            return result;
        }

        Map<TopicPartition, OffsetAndMetadata> filteredCommitted = new LinkedHashMap<>();
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committedOffsets.entrySet()) {
            TopicPartition partition = entry.getKey();
            if (partition != null && dataTopics.contains(partition.topic())) {
                filteredCommitted.put(partition, entry.getValue());
            }
        }

        if (filteredCommitted.isEmpty()) {
            result.put("groupFound", true);
            return result;
        }

        Map<TopicPartition, OffsetSpec> latestRequest = new HashMap<>();
        for (TopicPartition partition : filteredCommitted.keySet()) {
            latestRequest.put(partition, OffsetSpec.latest());
        }
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets = adminClient.listOffsets(latestRequest)
                .all()
                .get(5, TimeUnit.SECONDS);

        Map<String, Map<String, Object>> topicStats = new LinkedHashMap<>();
        long totalLag = 0L;
        int partitionCount = 0;
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : filteredCommitted.entrySet()) {
            TopicPartition partition = entry.getKey();
            OffsetAndMetadata committed = entry.getValue();
            ListOffsetsResult.ListOffsetsResultInfo latestInfo = latestOffsets.get(partition);

            long committedOffset = committed == null ? 0L : Math.max(committed.offset(), 0L);
            long latestOffset = latestInfo == null ? committedOffset : Math.max(latestInfo.offset(), committedOffset);
            long lag = Math.max(latestOffset - committedOffset, 0L);

            Map<String, Object> topicItem = topicStats.get(partition.topic());
            if (topicItem == null) {
                topicItem = new LinkedHashMap<>();
                topicItem.put("topic", partition.topic());
                topicItem.put("lag", 0L);
                topicItem.put("partitions", 0);
                topicItem.put("latestOffset", 0L);
                topicItem.put("committedOffset", 0L);
                topicStats.put(partition.topic(), topicItem);
            }

            topicItem.put("lag", parseLong(topicItem.get("lag")) + lag);
            topicItem.put("partitions", parseInt(topicItem.get("partitions")) + 1);
            topicItem.put("latestOffset", parseLong(topicItem.get("latestOffset")) + latestOffset);
            topicItem.put("committedOffset", parseLong(topicItem.get("committedOffset")) + committedOffset);

            partitionCount++;
            totalLag += lag;
        }

        List<Map<String, Object>> topics = new ArrayList<>(topicStats.values());
        topics.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                return Long.compare(parseLong(right.get("lag")), parseLong(left.get("lag")));
            }
        });

        result.put("groupFound", true);
        result.put("totalLag", totalLag);
        result.put("topicCount", topics.size());
        result.put("partitionCount", partitionCount);
        result.put("topics", topics);
        return result;
    }

    private Map<String, Object> inspectDeadLetter(AdminClient adminClient, Set<String> dataTopics) throws Exception {
        Map<String, Object> result = emptyDeadLetter();
        if (dataTopics.isEmpty()) {
            return result;
        }

        List<Map<String, Object>> topicRows = new ArrayList<>();
        long totalMessages = 0L;
        String suffix = StringUtils.hasText(streamKafkaConsumerProperties.getDeadLetterSuffix())
                ? streamKafkaConsumerProperties.getDeadLetterSuffix().trim()
                : ".dlt";

        for (String topic : dataTopics) {
            String deadLetterTopic = topic + suffix;
            long topicMessages = countTopicMessages(adminClient, deadLetterTopic);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("topic", deadLetterTopic);
            row.put("messages", Math.max(topicMessages, 0L));
            row.put("exists", topicMessages >= 0);
            topicRows.add(row);
            if (topicMessages > 0) {
                totalMessages += topicMessages;
            }
        }

        topicRows.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                return Long.compare(parseLong(right.get("messages")), parseLong(left.get("messages")));
            }
        });

        result.put("totalMessages", totalMessages);
        result.put("topicCount", topicRows.size());
        result.put("topics", topicRows);
        return result;
    }

    private long countTopicMessages(AdminClient adminClient, String topic) throws Exception {
        DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(Collections.singletonList(topic));
        Map<String, TopicDescription> descriptions;
        try {
            descriptions = describeTopicsResult.allTopicNames().get(5, TimeUnit.SECONDS);
        } catch (ExecutionException executionException) {
            return -1L;
        }
        TopicDescription description = descriptions.get(topic);
        if (description == null || description.partitions() == null || description.partitions().isEmpty()) {
            return 0L;
        }

        Map<TopicPartition, OffsetSpec> latestRequest = new HashMap<>();
        for (int index = 0; index < description.partitions().size(); index++) {
            latestRequest.put(new TopicPartition(topic, index), OffsetSpec.latest());
        }

        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets = adminClient.listOffsets(latestRequest)
                .all()
                .get(5, TimeUnit.SECONDS);

        long sum = 0L;
        for (ListOffsetsResult.ListOffsetsResultInfo info : latestOffsets.values()) {
            if (info != null && info.offset() > 0) {
                sum += info.offset();
            }
        }
        return sum;
    }

    private List<Map<String, Object>> buildAlerts(Map<String, Object> consumerLag, Map<String, Object> deadLetter) {
        List<Map<String, Object>> alerts = new ArrayList<>();

        long lag = parseLong(consumerLag.get("totalLag"));
        if (lag >= streamKafkaConsumerProperties.getLagCriticalThreshold()) {
            alerts.add(buildAlert("critical", "consumer_lag_critical", "消费者积压达到严重阈值", lag, streamKafkaConsumerProperties.getLagCriticalThreshold()));
        } else if (lag >= streamKafkaConsumerProperties.getLagWarnThreshold()) {
            alerts.add(buildAlert("warning", "consumer_lag_warning", "消费者积压超过预警阈值", lag, streamKafkaConsumerProperties.getLagWarnThreshold()));
        }

        long deadLetterCount = parseLong(deadLetter.get("totalMessages"));
        if (deadLetterCount >= streamKafkaConsumerProperties.getDeadLetterCriticalThreshold()) {
            alerts.add(buildAlert("critical", "dead_letter_critical", "死信消息量达到严重阈值", deadLetterCount, streamKafkaConsumerProperties.getDeadLetterCriticalThreshold()));
        } else if (deadLetterCount >= streamKafkaConsumerProperties.getDeadLetterWarnThreshold()) {
            alerts.add(buildAlert("warning", "dead_letter_warning", "死信队列出现消息", deadLetterCount, streamKafkaConsumerProperties.getDeadLetterWarnThreshold()));
        }

        return alerts;
    }

    private Map<String, Object> buildAlert(String level, String code, String message, long current, long threshold) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("level", level);
        alert.put("code", code);
        alert.put("message", message);
        alert.put("metric", current);
        alert.put("threshold", threshold);
        return alert;
    }

    private Map<String, Object> emptyConsumerLag(String consumerGroupId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupId", consumerGroupId);
        result.put("groupFound", false);
        result.put("totalLag", 0L);
        result.put("topicCount", 0);
        result.put("partitionCount", 0);
        result.put("topics", Collections.emptyList());
        return result;
    }

    private Map<String, Object> emptyDeadLetter() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMessages", 0L);
        result.put("topicCount", 0);
        result.put("topics", Collections.emptyList());
        return result;
    }

    private boolean isMissingConsumerGroup(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        String message = throwable.getMessage();
        if (StringUtils.hasText(message)) {
            String normalized = message.toLowerCase();
            if (normalized.contains("group id not found")
                    || normalized.contains("groupidnotfound")
                    || normalized.contains("does not exist")) {
                return true;
            }
        }
        return isMissingConsumerGroup(throwable.getCause());
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

    private int parseInt(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception ignore) {
            return 0;
        }
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private static final class MonitorCacheEntry {
        private final Map<String, Object> payload;
        private final long expireAtMillis;

        private MonitorCacheEntry(Map<String, Object> payload, long expireAtMillis) {
            this.payload = payload;
            this.expireAtMillis = expireAtMillis;
        }
    }
}
