package com.ecommerce.service.impl;

import com.ecommerce.common.Constants;
import com.ecommerce.config.StreamRealtimeProperties;
import com.ecommerce.dto.RecommendationEventDTO;
import com.ecommerce.entity.UserBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class StreamRealtimeProjectionService {

    private static final Logger log = LoggerFactory.getLogger(StreamRealtimeProjectionService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String HOT_KEY_PREFIX = "stream:product:hot:";
    private static final String HOT_LAST_UPDATE_KEY = "stream:product:hot:lastUpdate";
    private static final String KPI_KEY_PREFIX = "stream:recommendation:kpi:date:";
    private static final String KPI_LATEST_KEY = "stream:recommendation:kpi:latest";
    private static final String KPI_LAST_UPDATE_KEY = "stream:recommendation:kpi:lastUpdate";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StreamRealtimeProperties streamRealtimeProperties;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    public void projectRecommendationEvent(Long userId, RecommendationEventDTO eventDTO, String normalizedEventType) {
        if (userId == null || userId <= 0 || eventDTO == null || !StringUtils.hasText(normalizedEventType)) {
            return;
        }
        LocalDateTime eventTime = eventDTO.getEventTime() == null ? LocalDateTime.now() : eventDTO.getEventTime();
        Long productId = eventDTO.getProductId();
        double weight = eventWeight(normalizedEventType, eventDTO.getDuration());
        String behaviorType = toRealtimeBehaviorType(normalizedEventType);
        project(userId, productId, behaviorType, eventTime, weight, isPurchaseEvent(normalizedEventType));
        incrementRecommendationKpi(userId, normalizedEventType, eventTime);
    }

    public void projectBehavior(UserBehavior behavior) {
        if (behavior == null || behavior.getUserId() == null || behavior.getUserId() <= 0) {
            return;
        }
        String behaviorType = normalizeText(behavior.getBehaviorType());
        if (!StringUtils.hasText(behaviorType)) {
            return;
        }
        LocalDateTime eventTime = behavior.getCreateTime() == null ? LocalDateTime.now() : behavior.getCreateTime();
        project(
                behavior.getUserId(),
                behavior.getProductId(),
                behaviorType,
                eventTime,
                behaviorWeight(behaviorType, behavior.getDuration()),
                Constants.BehaviorType.PURCHASE.equals(behaviorType)
        );
    }

    private void project(Long userId,
                         Long productId,
                         String behaviorType,
                         LocalDateTime eventTime,
                         double scoreDelta,
                         boolean purchaseEvent) {
        try {
            if (StringUtils.hasText(behaviorType)) {
                upsertUserBehavior(userId, behaviorType, eventTime);
                incrementRedisBehavior(userId, behaviorType);
            }

            ProductSnapshot product = loadProductSnapshot(productId);
            if (product == null) {
                return;
            }

            upsertCategoryPreference(userId, product, scoreDelta, eventTime);
            upsertProductHotness(product, scoreDelta, purchaseEvent, eventTime);
            incrementRedisCategory(userId, product.categoryName, scoreDelta);
            incrementRedisHotness(product.productId, scoreDelta);
        } catch (Exception exception) {
            log.warn("[StreamRealtimeProjection] projection failed userId={} productId={} behaviorType={}: {}",
                    userId, productId, behaviorType, exception.getMessage());
        }
    }

    private void upsertUserBehavior(Long userId, String behaviorType, LocalDateTime eventTime) {
        jdbcTemplate.update(
                "INSERT INTO stream_user_behavior_distribution " +
                        "(user_id, behavior_type, behavior_count, last_event_time) VALUES (?, ?, 1, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "behavior_count = behavior_count + 1, " +
                        "last_event_time = GREATEST(COALESCE(last_event_time, VALUES(last_event_time)), VALUES(last_event_time)), " +
                        "update_time = CURRENT_TIMESTAMP",
                userId, behaviorType, eventTime);
    }

    private void upsertCategoryPreference(Long userId, ProductSnapshot product, double scoreDelta, LocalDateTime eventTime) {
        if (product.categoryId == null || product.categoryId <= 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO stream_user_category_preference " +
                        "(user_id, category_id, category_name, preference_score, behavior_count, last_event_time) " +
                        "VALUES (?, ?, ?, ?, 1, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "category_name = VALUES(category_name), " +
                        "preference_score = GREATEST(0, preference_score + VALUES(preference_score)), " +
                        "behavior_count = behavior_count + 1, " +
                        "last_event_time = GREATEST(COALESCE(last_event_time, VALUES(last_event_time)), VALUES(last_event_time)), " +
                        "update_time = CURRENT_TIMESTAMP",
                userId, product.categoryId, product.categoryName, scoreDelta, eventTime);
    }

    private void upsertProductHotness(ProductSnapshot product,
                                      double scoreDelta,
                                      boolean purchaseEvent,
                                      LocalDateTime eventTime) {
        jdbcTemplate.update(
                "INSERT INTO stream_product_hotness_realtime " +
                        "(product_id, category_id, hot_score, behavior_count, purchase_count, last_event_time) " +
                        "VALUES (?, ?, ?, 1, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "category_id = VALUES(category_id), " +
                        "hot_score = GREATEST(0, hot_score + VALUES(hot_score)), " +
                        "behavior_count = behavior_count + 1, " +
                        "purchase_count = purchase_count + VALUES(purchase_count), " +
                        "last_event_time = GREATEST(COALESCE(last_event_time, VALUES(last_event_time)), VALUES(last_event_time)), " +
                        "update_time = CURRENT_TIMESTAMP",
                product.productId, product.categoryId, scoreDelta, purchaseEvent ? 1L : 0L, eventTime);
    }

    private void incrementRedisBehavior(Long userId, String behaviorType) {
        if (stringRedisTemplate == null) {
            return;
        }
        String key = streamRealtimeProperties.getBehaviorStatsKeyPrefix() + ":" + userId;
        stringRedisTemplate.opsForHash().increment(key, behaviorType, 1L);
        stringRedisTemplate.opsForValue().set("stream:user:feature:lastUpdate:" + userId, nowText() + " [local-projection]");
    }

    private void incrementRedisCategory(Long userId, String categoryName, double scoreDelta) {
        if (stringRedisTemplate == null || !StringUtils.hasText(categoryName)) {
            return;
        }
        String key = streamRealtimeProperties.getCategoryWeightKeyPrefix() + ":" + userId;
        stringRedisTemplate.opsForHash().increment(key, categoryName, scoreDelta);
    }

    private void incrementRedisHotness(Long productId, double scoreDelta) {
        if (stringRedisTemplate == null || productId == null || productId <= 0) {
            return;
        }
        double safeDelta = Math.max(0.01D, scoreDelta);
        for (String window : new String[]{"1m", "1h", "1d"}) {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_KEY_PREFIX + window, String.valueOf(productId), safeDelta);
            stringRedisTemplate.opsForValue().set(HOT_LAST_UPDATE_KEY + ":" + window, nowText());
        }
        stringRedisTemplate.opsForValue().set(HOT_LAST_UPDATE_KEY, nowText());
    }

    private void incrementRecommendationKpi(Long userId, String eventType, LocalDateTime eventTime) {
        if (stringRedisTemplate == null || !StringUtils.hasText(eventType)) {
            return;
        }
        String statDate = eventTime == null ? LocalDate.now().toString() : eventTime.toLocalDate().toString();
        Map<String, String> base = new LinkedHashMap<>();
        base.put("statDate", statDate);
        base.put("ts", nowText());
        base.put("lastUpdate", nowText());

        for (String key : new String[]{KPI_KEY_PREFIX + statDate, KPI_LATEST_KEY}) {
            stringRedisTemplate.opsForHash().putAll(key, base);
            if (Constants.RecommendationEventType.EXPOSURE.equals(eventType)) {
                stringRedisTemplate.opsForHash().increment(key, "exposureCount", 1L);
            } else if (Constants.RecommendationEventType.CLICK.equals(eventType)) {
                stringRedisTemplate.opsForHash().increment(key, "clickCount", 1L);
            } else if (Constants.RecommendationEventType.ORDER.equals(eventType)) {
                stringRedisTemplate.opsForHash().increment(key, "orderCount", 1L);
                if (userId != null && userId > 0) {
                    stringRedisTemplate.opsForHash().increment(key, "orderUserCount", 1L);
                }
            } else if (Constants.RecommendationEventType.REFUND.equals(eventType)) {
                stringRedisTemplate.opsForHash().increment(key, "refundCount", 1L);
            }
        }
        stringRedisTemplate.opsForValue().set(KPI_LAST_UPDATE_KEY, nowText());
    }

    private ProductSnapshot loadProductSnapshot(Long productId) {
        if (productId == null || productId <= 0) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT p.id AS product_id, p.category_id, COALESCE(c.name, '未分类') AS category_name " +
                            "FROM product p LEFT JOIN category c ON c.id = p.category_id " +
                            "WHERE p.id = ? AND p.deleted = 0 LIMIT 1",
                    (rs, rowNum) -> new ProductSnapshot(
                            rs.getLong("product_id"),
                            rs.getLong("category_id"),
                            rs.getString("category_name")),
                    productId);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private String toRealtimeBehaviorType(String eventType) {
        String normalized = normalizeText(eventType);
        if (Constants.RecommendationEventType.EXPOSURE.equals(normalized)) {
            return "exposure";
        }
        if (Constants.RecommendationEventType.CLICK.equals(normalized)
                || Constants.RecommendationEventType.DWELL.equals(normalized)) {
            return Constants.BehaviorType.VIEW;
        }
        if (Constants.RecommendationEventType.ADD_CART.equals(normalized)) {
            return Constants.BehaviorType.CART;
        }
        if (Constants.RecommendationEventType.ORDER.equals(normalized)) {
            return Constants.BehaviorType.PURCHASE;
        }
        if (Constants.RecommendationEventType.REFUND.equals(normalized)) {
            return "refund";
        }
        return normalized;
    }

    private double eventWeight(String eventType, Integer duration) {
        String normalized = normalizeText(eventType);
        if (Constants.RecommendationEventType.EXPOSURE.equals(normalized)) {
            return 0.2D;
        }
        if (Constants.RecommendationEventType.CLICK.equals(normalized)) {
            return 1.2D;
        }
        if (Constants.RecommendationEventType.DWELL.equals(normalized)) {
            return Math.max(0.6D, Math.min(3.0D, (duration == null ? 30D : duration) / 30D));
        }
        if (Constants.RecommendationEventType.ADD_CART.equals(normalized)) {
            return 3.0D;
        }
        if (Constants.RecommendationEventType.ORDER.equals(normalized)) {
            return 6.0D;
        }
        if (Constants.RecommendationEventType.REFUND.equals(normalized)) {
            return -4.0D;
        }
        return 1.0D;
    }

    private double behaviorWeight(String behaviorType, Integer duration) {
        String normalized = normalizeText(behaviorType);
        if (Constants.BehaviorType.VIEW.equals(normalized)) {
            return Math.max(0.5D, Math.min(2.0D, (duration == null ? 30D : duration) / 45D));
        }
        if (Constants.BehaviorType.FAVORITE.equals(normalized)) {
            return 2.5D;
        }
        if (Constants.BehaviorType.CART.equals(normalized)) {
            return 3.0D;
        }
        if (Constants.BehaviorType.PURCHASE.equals(normalized)) {
            return 6.0D;
        }
        return 1.0D;
    }

    private boolean isPurchaseEvent(String eventType) {
        return Constants.RecommendationEventType.ORDER.equals(eventType);
    }

    private String normalizeText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private static class ProductSnapshot {
        private final Long productId;
        private final Long categoryId;
        private final String categoryName;

        private ProductSnapshot(Long productId, Long categoryId, String categoryName) {
            this.productId = productId;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }
}
