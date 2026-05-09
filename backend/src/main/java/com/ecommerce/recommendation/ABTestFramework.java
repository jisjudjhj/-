package com.ecommerce.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.AnalyticsRecommendationExposure;
import com.ecommerce.entity.RecommendationEvent;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.AnalyticsRecommendationExposureMapper;
import com.ecommerce.mapper.RecommendationEventMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class ABTestFramework {

    private static final Logger log = LoggerFactory.getLogger(ABTestFramework.class);

    private static final String REDIS_PREFIX = "abtest:metrics:";
    private static final long METRICS_TTL_DAYS = 30;
    private static final long SPLITMIX64_GAMMA = 0x9E3779B97F4A7C15L;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserBehaviorMapper userBehaviorMapper;

    @Autowired
    private AnalyticsRecommendationExposureMapper analyticsRecommendationExposureMapper;

    @Autowired(required = false)
    private RecommendationEventMapper recommendationEventMapper;

    @Value("${abtest.stratified.new-user-behavior-threshold:12}")
    private int stratifiedNewUserBehaviorThreshold;

    @Value("${abtest.stratified.high-aov-threshold:300}")
    private double stratifiedHighAovThreshold;

    public enum ExperimentGroup {
        CONTROL("control", "热门对照组"),
        HYBRID("hybrid", "标准混合组"),
        CF_HEAVY("cf_heavy", "协同强化组");

        public final String code;
        public final String description;

        ExperimentGroup(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }

    @PostConstruct
    public void init() {
        log.info("[A/B Test] framework initialized with Redis TTL {} days", METRICS_TTL_DAYS);
    }

    public ExperimentGroup assignGroup(Long userId) {
        int bucket = resolveBucket(userId);

        if (bucket < 30) {
            return ExperimentGroup.CONTROL;
        } else if (bucket < 70) {
            return ExperimentGroup.HYBRID;
        } else {
            return ExperimentGroup.CF_HEAVY;
        }
    }

    public Map<String, Double> getWeights(ExperimentGroup group) {
        Map<String, Double> weights = new HashMap<>();
        switch (group) {
            case CONTROL:
                weights.put("collaborative", 0.0);
                weights.put("content", 0.0);
                weights.put("popularity", 1.0);
                break;
            case HYBRID:
                weights.put("collaborative", 0.3);
                weights.put("content", 0.45);
                weights.put("popularity", 0.25);
                break;
            case CF_HEAVY:
                weights.put("collaborative", 0.45);
                weights.put("content", 0.35);
                weights.put("popularity", 0.2);
                break;
            default:
                break;
        }
        return weights;
    }

    public void recordExposure(Long userId, String group, List<Long> productIds) {
        try {
            String expKey = REDIS_PREFIX + group + ":exposures";
            String userKey = REDIS_PREFIX + group + ":users";
            for (int i = 0; i < productIds.size(); i++) {
                redisUtil.increment(expKey);
            }
            redisUtil.addToSet(userKey, userId.toString());
            redisUtil.expire(expKey, METRICS_TTL_DAYS, TimeUnit.DAYS);
            redisUtil.expire(userKey, METRICS_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("[A/B Test] failed to record exposure: {}", e.getMessage());
        }
    }

    public void recordClick(Long userId, String group, Long productId) {
        try {
            String key = REDIS_PREFIX + group + ":clicks";
            redisUtil.increment(key);
            redisUtil.expire(key, METRICS_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("[A/B Test] failed to record click: {}", e.getMessage());
        }
    }

    public void recordAddToCart(Long userId, String group, Long productId) {
        try {
            String key = REDIS_PREFIX + group + ":addToCarts";
            redisUtil.increment(key);
            redisUtil.expire(key, METRICS_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("[A/B Test] failed to record add-to-cart: {}", e.getMessage());
        }
    }

    public void recordPurchase(Long userId, String group, Long productId) {
        try {
            String key = REDIS_PREFIX + group + ":purchases";
            redisUtil.increment(key);
            redisUtil.expire(key, METRICS_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("[A/B Test] failed to record purchase: {}", e.getMessage());
        }
    }

    public Map<String, Object> getReport() {
        try {
            Map<String, Object> factReport = buildExposureFactReport();
            if (hasReportData(factReport)) {
                log.info("[A/B Test] using recommendation exposure fact report");
                return factReport;
            }
        } catch (Throwable t) {
            log.error("[A/B Test] failed to build exposure fact report", t);
        }

        try {
            Map<String, Object> runtimeReport = buildRuntimeReport();
            if (hasReportData(runtimeReport)) {
                return runtimeReport;
            }
        } catch (Throwable t) {
            log.error("[A/B Test] failed to build runtime report", t);
        }

        try {
            Map<String, Object> historicalReport = buildHistoricalFallbackReport();
            if (hasReportData(historicalReport)) {
                log.info("[A/B Test] using historical behavior fallback report");
                return historicalReport;
            }
        } catch (Throwable t) {
            log.error("[A/B Test] failed to build historical fallback report", t);
        }

        log.warn("[A/B Test] all report sources unavailable, returning empty fallback report");
        return buildEmptyReport("degraded");
    }

    public void resetMetrics() {
        try {
            for (ExperimentGroup group : ExperimentGroup.values()) {
                String prefix = REDIS_PREFIX + group.code + ":";
                redisUtil.delete(prefix + "exposures");
                redisUtil.delete(prefix + "clicks");
                redisUtil.delete(prefix + "addToCarts");
                redisUtil.delete(prefix + "purchases");
                redisUtil.delete(prefix + "users");
            }
            log.info("[A/B Test] reset Redis metrics");
        } catch (Exception e) {
            log.error("[A/B Test] failed to reset metrics: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildRuntimeReport() {
        Map<String, GroupMetrics> metricsMap = initMetricsMap();

        for (ExperimentGroup group : ExperimentGroup.values()) {
            GroupMetrics metrics = metricsMap.get(group.code);
            metrics.exposures = getMetricValue(group.code, "exposures");
            metrics.clicks = getMetricValue(group.code, "clicks");
            metrics.addToCarts = getMetricValue(group.code, "addToCarts");
            metrics.purchases = getMetricValue(group.code, "purchases");
            long uniqueUsers = getSetSize(group.code, "users");
            if (uniqueUsers > 0) {
                metrics.explicitUniqueUsers = uniqueUsers;
            }
        }

        return buildReportFromMetrics(metricsMap, "redis");
    }

    private Map<String, Object> buildExposureFactReport() {
        Map<String, GroupMetrics> metricsMap = initMetricsMap();
        if (analyticsRecommendationExposureMapper == null) {
            return buildReportFromMetrics(metricsMap, "mysql");
        }

        List<AnalyticsRecommendationExposure> exposures = new ArrayList<>();
        try {
            exposures = analyticsRecommendationExposureMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsRecommendationExposure>()
                            .gt(AnalyticsRecommendationExposure::getUserId, 0L)
                            .isNotNull(AnalyticsRecommendationExposure::getProductId)
                            .orderByAsc(AnalyticsRecommendationExposure::getExposureTime)
                            .orderByAsc(AnalyticsRecommendationExposure::getId));

            for (AnalyticsRecommendationExposure exposure : exposures) {
                String groupCode = resolveGroupCode(exposure);
                if (groupCode == null) {
                    continue;
                }

                GroupMetrics metrics = metricsMap.get(groupCode);
                if (metrics == null) {
                    continue;
                }

                if (exposure.getUserId() != null) {
                    metrics.uniqueUsers.add(exposure.getUserId());
                }
                metrics.exposures++;
                if (exposure.getClickTime() != null) {
                    metrics.clicks++;
                }
                if (exposure.getCartTime() != null) {
                    metrics.addToCarts++;
                }
                if (exposure.getPurchaseTime() != null) {
                    metrics.purchases++;
                }
            }
        } catch (Exception e) {
            log.warn("[A/B Test] failed to build exposure fact report: {}", e.getMessage());
        }

        Map<String, Object> report = buildReportFromMetrics(metricsMap, "mysql");
        attachStratifiedBreakdown(report, exposures);
        return report;
    }

    private Map<String, Object> buildHistoricalFallbackReport() {
        Map<String, GroupMetrics> metricsMap = initMetricsMap();

        try {
            List<UserBehavior> behaviors = userBehaviorMapper.selectList(
                    new LambdaQueryWrapper<UserBehavior>()
                            .isNotNull(UserBehavior::getProductId)
                            .in(UserBehavior::getBehaviorType,
                                    Constants.BehaviorType.VIEW,
                                    Constants.BehaviorType.FAVORITE,
                                    Constants.BehaviorType.CART,
                                    Constants.BehaviorType.PURCHASE));

            for (UserBehavior behavior : behaviors) {
                ExperimentGroup group = assignGroup(behavior.getUserId());
                GroupMetrics metrics = metricsMap.get(group.code);
                metrics.uniqueUsers.add(behavior.getUserId());

                String behaviorType = behavior.getBehaviorType();
                if (Constants.BehaviorType.VIEW.equals(behaviorType)) {
                    metrics.exposures++;
                } else if (Constants.BehaviorType.FAVORITE.equals(behaviorType)) {
                    metrics.clicks++;
                } else if (Constants.BehaviorType.CART.equals(behaviorType)) {
                    metrics.clicks++;
                    metrics.addToCarts++;
                } else if (Constants.BehaviorType.PURCHASE.equals(behaviorType)) {
                    metrics.clicks++;
                    metrics.addToCarts++;
                    metrics.purchases++;
                }
            }
        } catch (Exception e) {
            log.warn("[A/B Test] failed to build historical fallback report: {}", e.getMessage());
        }

        return buildReportFromMetrics(metricsMap, "historical");
    }

    private void attachStratifiedBreakdown(Map<String, Object> report,
                                           List<AnalyticsRecommendationExposure> exposures) {
        if (report == null || report.isEmpty() || exposures == null || exposures.isEmpty()) {
            return;
        }
        Set<Long> userIds = exposures.stream()
                .map(AnalyticsRecommendationExposure::getUserId)
                .filter(userId -> userId != null && userId > 0)
                .collect(java.util.stream.Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }

        Map<Long, UserStrata> userStrata = buildUserStrata(userIds);
        Map<String, Map<String, SegmentMetrics>> lifecycleByGroup = new LinkedHashMap<>();
        Map<String, Map<String, SegmentMetrics>> valueByGroup = new LinkedHashMap<>();
        for (ExperimentGroup group : ExperimentGroup.values()) {
            lifecycleByGroup.put(group.code, new LinkedHashMap<>());
            valueByGroup.put(group.code, new LinkedHashMap<>());
        }

        for (AnalyticsRecommendationExposure exposure : exposures) {
            String groupCode = resolveGroupCode(exposure);
            if (!isKnownGroup(groupCode)) {
                continue;
            }
            Long userId = exposure.getUserId();
            if (userId == null || userId <= 0) {
                continue;
            }
            UserStrata strata = userStrata.getOrDefault(userId, UserStrata.defaultStrata());
            SegmentMetrics lifecycleMetrics = lifecycleByGroup
                    .get(groupCode)
                    .computeIfAbsent(strata.lifecycleSegment, ignored -> new SegmentMetrics());
            updateSegmentMetrics(lifecycleMetrics, exposure);

            SegmentMetrics valueMetrics = valueByGroup
                    .get(groupCode)
                    .computeIfAbsent(strata.valueSegment, ignored -> new SegmentMetrics());
            updateSegmentMetrics(valueMetrics, exposure);
        }

        for (ExperimentGroup group : ExperimentGroup.values()) {
            Object rawGroupReport = report.get(group.code);
            if (!(rawGroupReport instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> groupReport = (Map<String, Object>) rawGroupReport;
            groupReport.put("stratifiedLifecycle", buildSegmentReport(lifecycleByGroup.get(group.code)));
            groupReport.put("stratifiedValue", buildSegmentReport(valueByGroup.get(group.code)));
        }
    }

    private Map<Long, UserStrata> buildUserStrata(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> behaviorCountByUser = new HashMap<>();
        try {
            List<UserBehavior> behaviors = userBehaviorMapper.selectList(
                    new LambdaQueryWrapper<UserBehavior>()
                            .select(UserBehavior::getUserId, UserBehavior::getBehaviorType)
                            .in(UserBehavior::getUserId, userIds));
            if (behaviors != null) {
                for (UserBehavior behavior : behaviors) {
                    if (behavior == null || behavior.getUserId() == null) {
                        continue;
                    }
                    behaviorCountByUser.merge(behavior.getUserId(), 1L, Long::sum);
                }
            }
        } catch (Exception e) {
            log.warn("[A/B Test] stratified behavior snapshot unavailable: {}", e.getMessage());
        }

        Map<Long, Double> averageOrderAmountByUser = new HashMap<>();
        if (recommendationEventMapper != null) {
            try {
                List<RecommendationEvent> orderEvents = recommendationEventMapper.selectList(
                        new LambdaQueryWrapper<RecommendationEvent>()
                                .select(RecommendationEvent::getUserId,
                                        RecommendationEvent::getAmount,
                                        RecommendationEvent::getEventType)
                                .in(RecommendationEvent::getUserId, userIds)
                                .eq(RecommendationEvent::getEventType, Constants.RecommendationEventType.ORDER));
                Map<Long, Double> amountSumByUser = new HashMap<>();
                Map<Long, Integer> amountCountByUser = new HashMap<>();
                if (orderEvents != null) {
                    for (RecommendationEvent event : orderEvents) {
                        if (event == null || event.getUserId() == null || event.getAmount() == null) {
                            continue;
                        }
                        amountSumByUser.merge(event.getUserId(), event.getAmount().doubleValue(), Double::sum);
                        amountCountByUser.merge(event.getUserId(), 1, Integer::sum);
                    }
                }
                for (Map.Entry<Long, Double> entry : amountSumByUser.entrySet()) {
                    int count = amountCountByUser.getOrDefault(entry.getKey(), 0);
                    if (count > 0) {
                        averageOrderAmountByUser.put(entry.getKey(), entry.getValue() / count);
                    }
                }
            } catch (Exception e) {
                log.warn("[A/B Test] stratified order snapshot unavailable: {}", e.getMessage());
            }
        }

        Map<Long, UserStrata> result = new HashMap<>();
        long safeNewUserThreshold = Math.max(1, stratifiedNewUserBehaviorThreshold);
        double safeHighAovThreshold = Math.max(1D, stratifiedHighAovThreshold);
        for (Long userId : userIds) {
            long behaviorCount = behaviorCountByUser.getOrDefault(userId, 0L);
            String lifecycleSegment = behaviorCount <= safeNewUserThreshold ? "new_user" : "returning_user";
            double avgOrderAmount = averageOrderAmountByUser.getOrDefault(userId, 0D);
            String valueSegment = avgOrderAmount >= safeHighAovThreshold ? "high_aov" : "low_aov";
            result.put(userId, new UserStrata(lifecycleSegment, valueSegment));
        }
        return result;
    }

    private void updateSegmentMetrics(SegmentMetrics metrics, AnalyticsRecommendationExposure exposure) {
        if (metrics == null || exposure == null) {
            return;
        }
        if (exposure.getUserId() != null) {
            metrics.uniqueUsers.add(exposure.getUserId());
        }
        metrics.exposures++;
        if (exposure.getClickTime() != null) {
            metrics.clicks++;
        }
        if (exposure.getCartTime() != null) {
            metrics.addToCarts++;
        }
        if (exposure.getPurchaseTime() != null) {
            metrics.purchases++;
        }
    }

    private Map<String, Object> buildSegmentReport(Map<String, SegmentMetrics> segmentMetricsMap) {
        if (segmentMetricsMap == null || segmentMetricsMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, SegmentMetrics> entry : segmentMetricsMap.entrySet()) {
            SegmentMetrics metrics = entry.getValue();
            if (metrics == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segment", entry.getKey());
            row.put("uniqueUsers", metrics.uniqueUsers.size());
            row.put("totalExposures", metrics.exposures);
            row.put("totalClicks", metrics.clicks);
            row.put("totalAddToCarts", metrics.addToCarts);
            row.put("totalPurchases", metrics.purchases);
            row.put("ctr", formatRatio(metrics.clicks, metrics.exposures));
            row.put("conversionRate", formatRatio(metrics.purchases, metrics.clicks));
            row.put("overallConversion", formatRatio(metrics.purchases, metrics.exposures));
            result.put(entry.getKey(), row);
        }
        return result;
    }

    private Map<String, GroupMetrics> initMetricsMap() {
        Map<String, GroupMetrics> metricsMap = new LinkedHashMap<>();
        for (ExperimentGroup group : ExperimentGroup.values()) {
            metricsMap.put(group.code, new GroupMetrics(group));
        }
        return metricsMap;
    }

    private String resolveGroupCode(AnalyticsRecommendationExposure exposure) {
        if (exposure == null) {
            return null;
        }

        String rawGroup = normalizeGroupCode(exposure.getExperimentGroup());
        if ("disabled".equals(rawGroup)) {
            return null;
        }
        if (rawGroup != null && isKnownGroup(rawGroup)) {
            return rawGroup;
        }

        Long userId = exposure.getUserId();
        if (userId == null || userId <= 0) {
            return null;
        }
        return assignGroup(userId).code;
    }

    private boolean isKnownGroup(String groupCode) {
        return ExperimentGroup.CONTROL.code.equals(groupCode)
                || ExperimentGroup.HYBRID.code.equals(groupCode)
                || ExperimentGroup.CF_HEAVY.code.equals(groupCode);
    }

    private String normalizeGroupCode(String groupCode) {
        if (groupCode == null) {
            return null;
        }
        String trimmed = groupCode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> buildReportFromMetrics(Map<String, GroupMetrics> metricsMap, String dataSource) {
        Map<String, Object> report = new LinkedHashMap<>();

        for (ExperimentGroup group : ExperimentGroup.values()) {
            GroupMetrics metrics = metricsMap.getOrDefault(group.code, new GroupMetrics(group));
            long uniqueUsers = metrics.explicitUniqueUsers != null
                    ? metrics.explicitUniqueUsers
                    : metrics.uniqueUsers.size();

            Map<String, Object> groupReport = new LinkedHashMap<>();
            groupReport.put("groupCode", group.code);
            groupReport.put("description", group.description);
            groupReport.put("uniqueUsers", uniqueUsers);
            groupReport.put("totalExposures", metrics.exposures);
            groupReport.put("totalClicks", metrics.clicks);
            groupReport.put("totalAddToCarts", metrics.addToCarts);
            groupReport.put("totalPurchases", metrics.purchases);
            groupReport.put("dataSource", dataSource);
            groupReport.put("ctr", formatRatio(metrics.clicks, metrics.exposures));
            groupReport.put("conversionRate", formatRatio(metrics.purchases, metrics.clicks));
            groupReport.put("overallConversion", formatRatio(metrics.purchases, metrics.exposures));

            report.put(group.code, groupReport);
        }

        return report;
    }

    private Map<String, Object> buildEmptyReport(String dataSource) {
        Map<String, Object> report = new LinkedHashMap<>();

        for (ExperimentGroup group : ExperimentGroup.values()) {
            Map<String, Object> groupReport = new LinkedHashMap<>();
            groupReport.put("groupCode", group.code);
            groupReport.put("description", group.description);
            groupReport.put("uniqueUsers", 0L);
            groupReport.put("totalExposures", 0L);
            groupReport.put("totalClicks", 0L);
            groupReport.put("totalAddToCarts", 0L);
            groupReport.put("totalPurchases", 0L);
            groupReport.put("dataSource", dataSource);
            groupReport.put("ctr", "0.0000");
            groupReport.put("conversionRate", "0.0000");
            groupReport.put("overallConversion", "0.0000");
            report.put(group.code, groupReport);
        }

        return report;
    }

    private boolean hasReportData(Map<String, Object> report) {
        for (Object value : report.values()) {
            if (!(value instanceof Map)) {
                continue;
            }

            Map<?, ?> groupReport = (Map<?, ?>) value;
            if (readLong(groupReport.get("uniqueUsers")) > 0
                    || readLong(groupReport.get("totalExposures")) > 0
                    || readLong(groupReport.get("totalClicks")) > 0
                    || readLong(groupReport.get("totalAddToCarts")) > 0
                    || readLong(groupReport.get("totalPurchases")) > 0) {
                return true;
            }
        }
        return false;
    }

    private String formatRatio(long numerator, long denominator) {
        return denominator > 0
                ? String.format(Locale.ROOT, "%.4f", (double) numerator / denominator)
                : "0.0000";
    }

    private long getMetricValue(String group, String metric) {
        try {
            Object val = redisUtil.get(REDIS_PREFIX + group + ":" + metric);
            if (val != null) {
                return Long.parseLong(val.toString());
            }
        } catch (Exception e) {
            log.warn("[AB-Test] 读取指标失败 group={} metric={}: {}", group, metric, e.getMessage());
            return 0L;
        }
        return 0L;
    }

    private long getSetSize(String group, String metric) {
        try {
            return redisUtil.getSetSize(REDIS_PREFIX + group + ":" + metric);
        } catch (Exception e) {
            log.warn("[AB-Test] 读取集合大小失败 group={} metric={}: {}", group, metric, e.getMessage());
            return 0L;
        }
    }

    private int resolveBucket(Long userId) {
        long normalizedUserId = userId == null ? 0L : userId;
        long mixed = mix64(normalizedUserId);
        return (int) Math.floorMod(mixed, 100L);
    }

    private long mix64(long value) {
        long z = value + SPLITMIX64_GAMMA;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private long readLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.debug("[AB-Test] 数值解析失败: {}", value);
            return 0L;
        }
    }

    private static class SegmentMetrics {
        private final Set<Long> uniqueUsers = new HashSet<>();
        private long exposures;
        private long clicks;
        private long addToCarts;
        private long purchases;
    }

    private static class UserStrata {
        private final String lifecycleSegment;
        private final String valueSegment;

        private UserStrata(String lifecycleSegment, String valueSegment) {
            this.lifecycleSegment = lifecycleSegment;
            this.valueSegment = valueSegment;
        }

        private static UserStrata defaultStrata() {
            return new UserStrata("new_user", "low_aov");
        }
    }

    private static class GroupMetrics {
        private final ExperimentGroup group;
        private final Set<Long> uniqueUsers = new HashSet<>();
        private Long explicitUniqueUsers;
        private long exposures;
        private long clicks;
        private long addToCarts;
        private long purchases;

        private GroupMetrics(ExperimentGroup group) {
            this.group = group;
        }
    }
}
