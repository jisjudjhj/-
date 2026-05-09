package com.ecommerce.service;

import com.ecommerce.dto.RecommendationEventDTO;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserBehavior;

import java.util.List;
import java.util.Map;

public interface RecommendationService {

    List<Product> getPersonalRecommendations(Long userId, int limit);

    default List<Product> getSimilarProducts(Long productId, int limit) {
        return getSimilarProducts(null, productId, limit);
    }

    List<Product> getSimilarProducts(Long userId, Long productId, int limit);

    default List<Product> getHotRecommendations(int limit) {
        return getHotRecommendations(null, limit);
    }

    List<Product> getHotRecommendations(Long userId, int limit);

    List<Product> rerankFeedRecommendations(Long userId, List<Product> products, int limit, String scene, String algorithmTag);

    List<Product> guessYouLike(Long userId, int limit);

    default List<Product> guessYouLike(Long userId, int limit, boolean forcePersonalized) {
        return guessYouLike(userId, limit);
    }

    Map<String, Object> getPersonalRecommendationsWithExplanation(Long userId, int limit);

    default Map<String, Object> getPersonalRecommendationsWithExplanation(Long userId,
                                                                          int limit,
                                                                          boolean forcePersonalized) {
        return getPersonalRecommendationsWithExplanation(userId, limit);
    }

    Map<String, Object> getRealtimeRecommendationDashboard(Long userId, int limit);

    default Map<String, Object> getRealtimeRecommendationDashboard(Long userId,
                                                                   int limit,
                                                                   boolean forcePersonalized) {
        return getRealtimeRecommendationDashboard(userId, limit);
    }

    void recordBehavior(UserBehavior behavior);

    void recordRecommendationEvent(Long userId, RecommendationEventDTO eventDTO);

    Map<String, Object> getRecommendationMetrics(int days);

    List<UserBehavior> getUserBehaviors(Long userId, String type, int page, int size);

    List<Product> getProductsByIds(List<Long> productIds);
}
