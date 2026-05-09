package com.ecommerce.recommendation;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.dto.RecommendationEventDTO;
import com.ecommerce.entity.AnalyticsRecommendationExposure;
import com.ecommerce.mapper.AnalyticsRecommendationExposureMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationAttributionService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationAttributionService.class);

    @Autowired
    private AnalyticsRecommendationExposureMapper analyticsRecommendationExposureMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public void persistExposureFromRecommendationEvent(Long userId,
                                                       RecommendationEventDTO eventDTO,
                                                       LocalDateTime eventTime) {
        if (userId == null || userId <= 0 || eventDTO == null
                || eventDTO.getProductId() == null
                || !StringUtils.hasText(eventDTO.getRecommendationToken())) {
            return;
        }
        try {
            AnalyticsRecommendationExposure exists = analyticsRecommendationExposureMapper.selectOne(
                    new LambdaQueryWrapper<AnalyticsRecommendationExposure>()
                            .eq(AnalyticsRecommendationExposure::getExposureToken, eventDTO.getRecommendationToken())
                            .last("LIMIT 1"));
            if (exists != null) {
                return;
            }
            JSONObject metadata = parseRecommendationMetadata(eventDTO.getMetadata());
            AnalyticsRecommendationExposure exposure = new AnalyticsRecommendationExposure();
            exposure.setExposureToken(eventDTO.getRecommendationToken());
            exposure.setRequestToken(firstNonEmpty(
                    stringValue(metadata.get("requestToken")),
                    stringValue(metadata.get("sessionId")),
                    eventDTO.getTraceId(),
                    "event:" + eventDTO.getRecommendationToken()));
            exposure.setUserId(userId);
            exposure.setProductId(eventDTO.getProductId());
            exposure.setScene(firstNonEmpty(eventDTO.getScene(), "unknown"));
            exposure.setRankNo(Math.max(1, intFrom(metadata.get("position"), 1)));
            exposure.setAlgorithm(firstNonEmpty(
                    stringValue(metadata.get("algorithm")),
                    stringValue(metadata.get("sourceType")),
                    "search_personalized_rank"));
            exposure.setSourceType(firstNonEmpty(
                    stringValue(metadata.get("sourceType")),
                    "search_personalized"));
            exposure.setReasonType(firstNonEmpty(
                    stringValue(metadata.get("reasonType")),
                    "SEARCH_PERSONALIZED"));
            exposure.setModelVersion(firstNonEmpty(
                    stringValue(metadata.get("modelVersion")),
                    "search-personalized-v1"));
            exposure.setExperimentGroup(eventDTO.getExperimentGroup());
            exposure.setSegmentCode(stringValue(metadata.get("segmentCode")));
            exposure.setSegmentName(stringValue(metadata.get("segmentName")));
            exposure.setExposureTime(eventTime == null ? LocalDateTime.now() : eventTime);
            analyticsRecommendationExposureMapper.insert(exposure);
        } catch (Exception exception) {
            log.warn("[RecommendationEvent] persist exposure failed token={} user={} product={}: {}",
                    eventDTO.getRecommendationToken(), userId, eventDTO.getProductId(), exception.getMessage());
        }
    }

    public void attributeOrderEventToRecentExposures(Long userId,
                                                     RecommendationEventDTO eventDTO,
                                                     String eventType,
                                                     LocalDateTime eventTime) {
        if (jdbcTemplate == null || userId == null || eventDTO == null || eventDTO.getOrderId() == null) {
            return;
        }
        try {
            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                    "SELECT product_id, subtotal FROM order_item WHERE order_id = ?",
                    eventDTO.getOrderId());
            if (items == null || items.isEmpty()) {
                return;
            }
            LocalDateTime cutoff = (eventTime == null ? LocalDateTime.now() : eventTime).minusDays(7);
            for (Map<String, Object> item : items) {
                Long productId = longFrom(item.get("product_id"));
                if (productId == null) {
                    continue;
                }
                AnalyticsRecommendationExposure exposure = analyticsRecommendationExposureMapper.selectOne(
                        new LambdaQueryWrapper<AnalyticsRecommendationExposure>()
                                .eq(AnalyticsRecommendationExposure::getUserId, userId)
                                .eq(AnalyticsRecommendationExposure::getProductId, productId)
                                .ge(AnalyticsRecommendationExposure::getExposureTime, cutoff)
                                .orderByDesc(AnalyticsRecommendationExposure::getExposureTime)
                                .last("LIMIT 1"));
                if (exposure == null) {
                    continue;
                }
                if (Constants.RecommendationEventType.ORDER.equals(eventType)
                        && exposure.getPurchaseTime() == null) {
                    exposure.setPurchaseTime(eventTime == null ? LocalDateTime.now() : eventTime);
                    exposure.setOrderId(eventDTO.getOrderId());
                    analyticsRecommendationExposureMapper.updateById(exposure);
                } else if (Constants.RecommendationEventType.REFUND.equals(eventType)
                        && exposure.getOrderId() == null) {
                    exposure.setOrderId(eventDTO.getOrderId());
                    analyticsRecommendationExposureMapper.updateById(exposure);
                }
            }
        } catch (Exception exception) {
            log.warn("[RecommendationEvent] order attribution failed user={} order={} type={}: {}",
                    userId, eventDTO.getOrderId(), eventType, exception.getMessage());
        }
    }

    public Map<String, Object> buildAttributionHealth(LocalDateTime startTime) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("windowStart", startTime);
        health.put("status", "unknown");
        health.put("exposureTokenCount", 0L);
        health.put("exposureFactCount", 0L);
        health.put("tokenFactRate", 0D);
        health.put("downstreamTokenEventCount", 0L);
        health.put("downstreamMatchedEventCount", 0L);
        health.put("downstreamMatchRate", 0D);
        health.put("orderEventCount", 0L);
        health.put("orderAttributedCount", 0L);
        health.put("orderAttributionRate", 0D);
        if (jdbcTemplate == null) {
            health.put("message", "JdbcTemplate unavailable");
            return health;
        }
        try {
            Long exposureTokenCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT recommendation_token) FROM recommendation_event " +
                            "WHERE create_time >= ? AND event_type = 'exposure' " +
                            "AND recommendation_token IS NOT NULL AND recommendation_token <> ''",
                    Long.class,
                    startTime);
            Long exposureFactCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT e.recommendation_token) FROM recommendation_event e " +
                            "JOIN analytics_recommendation_exposure x ON x.exposure_token = e.recommendation_token " +
                            "WHERE e.create_time >= ? AND e.event_type = 'exposure' " +
                            "AND e.recommendation_token IS NOT NULL AND e.recommendation_token <> ''",
                    Long.class,
                    startTime);
            Long downstreamTokenEventCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM recommendation_event " +
                            "WHERE create_time >= ? AND event_type IN ('click','dwell','add_cart','order','refund') " +
                            "AND recommendation_token IS NOT NULL AND recommendation_token <> ''",
                    Long.class,
                    startTime);
            Long downstreamMatchedEventCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM recommendation_event e " +
                            "JOIN analytics_recommendation_exposure x ON x.exposure_token = e.recommendation_token " +
                            "WHERE e.create_time >= ? AND e.event_type IN ('click','dwell','add_cart','order','refund') " +
                            "AND e.recommendation_token IS NOT NULL AND e.recommendation_token <> ''",
                    Long.class,
                    startTime);
            Long orderEventCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM recommendation_event " +
                            "WHERE create_time >= ? AND event_type IN ('order','pay','paid','payment')",
                    Long.class,
                    startTime);
            Long orderAttributedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM analytics_recommendation_exposure " +
                            "WHERE exposure_time >= ? AND purchase_time IS NOT NULL",
                    Long.class,
                    startTime);

            long tokenCount = exposureTokenCount == null ? 0L : exposureTokenCount;
            long factCount = exposureFactCount == null ? 0L : exposureFactCount;
            long downstreamCount = downstreamTokenEventCount == null ? 0L : downstreamTokenEventCount;
            long matchedCount = downstreamMatchedEventCount == null ? 0L : downstreamMatchedEventCount;
            long orderCount = orderEventCount == null ? 0L : orderEventCount;
            long attributedCount = orderAttributedCount == null ? 0L : orderAttributedCount;
            double tokenFactRate = ratioPercent(factCount, tokenCount);
            double downstreamMatchRate = ratioPercent(matchedCount, downstreamCount);
            double orderAttributionRate = ratioPercent(attributedCount, orderCount);

            health.put("exposureTokenCount", tokenCount);
            health.put("exposureFactCount", factCount);
            health.put("tokenFactRate", tokenFactRate);
            health.put("downstreamTokenEventCount", downstreamCount);
            health.put("downstreamMatchedEventCount", matchedCount);
            health.put("downstreamMatchRate", downstreamMatchRate);
            health.put("orderEventCount", orderCount);
            health.put("orderAttributedCount", attributedCount);
            health.put("orderAttributionRate", orderAttributionRate);
            health.put("status", tokenFactRate >= 95D && downstreamMatchRate >= 85D ? "healthy" : "watch");
            health.put("message", tokenCount <= 0 ? "暂无曝光 token 样本" : "推荐 token 与归因事实表匹配率已计算");
        } catch (Exception exception) {
            health.put("status", "error");
            health.put("message", exception.getMessage());
        }
        return health;
    }

    private JSONObject parseRecommendationMetadata(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(metadata);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private int intFrom(Object rawValue, int defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(rawValue));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Long longFrom(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(rawValue));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringValue(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue).trim();
        return StringUtils.hasText(value) ? value : null;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private double ratioPercent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round((numerator * 10000D / denominator)) / 100D;
    }
}
