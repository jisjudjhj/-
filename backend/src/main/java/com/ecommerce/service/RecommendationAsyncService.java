package com.ecommerce.service;

import com.ecommerce.dto.RecommendationEventDTO;
import com.ecommerce.entity.UserBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RecommendationAsyncService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationAsyncService.class);

    private final RecommendationService recommendationService;

    public RecommendationAsyncService(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Async("recommendationTaskExecutor")
    public void recordBehaviorAsync(UserBehavior behavior) {
        try {
            recommendationService.recordBehavior(behavior);
        } catch (Exception ex) {
            log.warn("[RecommendationAsync] recordBehavior failed, userId={}, productId={}, type={}, error={}",
                    behavior == null ? null : behavior.getUserId(),
                    behavior == null ? null : behavior.getProductId(),
                    behavior == null ? null : behavior.getBehaviorType(),
                    ex.getMessage());
        }
    }

    @Async("recommendationTaskExecutor")
    public void recordRecommendationEventAsync(Long userId, RecommendationEventDTO eventDTO) {
        try {
            recommendationService.recordRecommendationEvent(userId, eventDTO);
        } catch (Exception ex) {
            log.warn("[RecommendationAsync] recordRecommendationEvent failed, userId={}, type={}, productId={}, error={}",
                    userId,
                    eventDTO == null ? null : eventDTO.getEventType(),
                    eventDTO == null ? null : eventDTO.getProductId(),
                    ex.getMessage());
        }
    }
}
