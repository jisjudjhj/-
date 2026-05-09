package com.ecommerce.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "stream.kafka")
public class StreamKafkaConsumerProperties {

    /**
     * Master switch for Kafka DWS consumer.
     */
    private boolean enabled = false;

    /**
     * DWS topic: user behavior distribution.
     */
    private String userBehaviorTopic = "dws.user_behavior_distribution";

    /**
     * DWS topic: user category preference.
     */
    private String userCategoryTopic = "dws.user_category_preference";

    /**
     * DWS topic: product realtime hotness.
     */
    private String productHotnessTopic = "dws.product_hotness_realtime";

    /**
     * DWS topic: recommendation core metrics realtime.
     */
    private String recommendationCoreMetricsTopic = "dws.recommendation_core_metrics_realtime";

    /**
     * DWD topic: product changed event.
     */
    private String productChangedTopic = "dwd.product_changed_event";

    /**
     * CDC topic: product table change capture.
     */
    private String productCdcTopic = "cdc.product";

    /**
     * Dead-letter topic suffix.
     */
    private String deadLetterSuffix = ".dlt";

    /**
     * Consumer group id.
     */
    private String groupId = "ecommerce-stream-dws";

    /**
     * Consumer lag warning threshold.
     */
    private long lagWarnThreshold = 1000L;

    /**
     * Consumer lag critical threshold.
     */
    private long lagCriticalThreshold = 5000L;

    /**
     * Dead-letter message warning threshold.
     */
    private long deadLetterWarnThreshold = 1L;

    /**
     * Dead-letter message critical threshold.
     */
    private long deadLetterCriticalThreshold = 20L;

    /**
     * Hot ranking last update stale warning threshold (seconds).
     */
    private long hotDataStaleSeconds = 180L;
}
