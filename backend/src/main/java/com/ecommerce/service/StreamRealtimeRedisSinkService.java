package com.ecommerce.service;

import java.util.List;
import java.util.Map;

public interface StreamRealtimeRedisSinkService {

    void acceptUserBehaviorDistribution(String rawJson);

    void acceptUserCategoryPreference(String rawJson);

    void acceptProductHotness(String rawJson);

    void acceptRecommendationCoreMetrics(String rawJson);

    Map<String, Object> getUserRealtimeSnapshot(Long userId);

    List<Map<String, Object>> getHotProducts(String window, long topN);

    Map<String, Object> getRecommendationCoreMetrics(String statDate);
}
