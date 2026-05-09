package com.ecommerce.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface StreamRealtimeFeatureService {

    /**
     * Returns behavior distribution from stream-updated Redis features.
     * Item shape: {behaviorType, count, productCount}
     */
    List<Map<String, Object>> getUserBehaviorStats(Long userId);

    /**
     * Returns category weights from stream-updated Redis features.
     * Shape: {categoryName -> weight}
     */
    Map<String, Double> getUserCategoryWeights(Long userId);

    /**
     * Returns tag set from stream-updated Redis features.
     */
    Set<String> getUserTags(Long userId);
}

