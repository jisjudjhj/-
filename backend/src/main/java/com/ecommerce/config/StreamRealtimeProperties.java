package com.ecommerce.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "stream.realtime")
public class StreamRealtimeProperties {

    /**
     * Whether stream realtime profile enrichment is enabled.
     */
    private boolean enabled = false;

    /**
     * Redis hash key prefix storing user behavior counters.
     * Full key: {behaviorStatsKeyPrefix}:{userId}
     */
    private String behaviorStatsKeyPrefix = "stream:user:behavior:dist";

    /**
     * Redis hash key prefix storing user category preference weights.
     * Full key: {categoryWeightKeyPrefix}:{userId}
     */
    private String categoryWeightKeyPrefix = "stream:user:category:weight";

    /**
     * Redis set key prefix storing user preference tags.
     * Full key: {tagSetKeyPrefix}:{userId}
     */
    private String tagSetKeyPrefix = "stream:user:tags";
}

