package com.ecommerce.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mq")
public class MqProperties {

    private boolean enabled = false;

    private int publisherBatchSize = 100;

    private long publishIntervalMs = 3000L;

    private int maxRetries = 6;

    private int retryDelaySeconds = 30;

    private int listenerMaxAttempts = 3;
}
