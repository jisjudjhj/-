package com.ecommerce.recommendation;

import com.ecommerce.entity.AnalyticsRecommendationResult;
import com.ecommerce.mapper.AnalyticsRecommendationResultMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.service.DataAnalysisService;
import com.ecommerce.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 离线批处理引擎
 *
 * 定时从 MySQL 读取用户行为数据，在内存中批量计算：
 * - 用户相似度矩阵（余弦相似度）
 * - 商品共现矩阵（Jaccard 归一化）
 * - 用户兴趣画像（品类偏好 + 标签偏好）
 *
 * 计算结果写入 Redis 供在线推荐服务低延迟读取。
 * 采用"分片计算 → 聚合 → 写入缓存"的批处理思路，
 * 数据量增长后可替换为分布式计算引擎（如 Spark MLlib / Flink）。
 */
@Component
@ConditionalOnProperty(name = "offline.enabled", havingValue = "true", matchIfMissing = true)
public class OfflineDataProcessor {

    private static final Logger log = LoggerFactory.getLogger(OfflineDataProcessor.class);

    public static final String CACHE_KEY_USER_SIM = "offline:user_similarity:";
    public static final String CACHE_KEY_PRODUCT_COOCCUR = "offline:product_cooccurrence:";
    public static final String CACHE_KEY_USER_PROFILE = "offline:user_profile:";
    public static final String CACHE_KEY_RFM = "offline:rfm_segments";
    public static final String CACHE_KEY_ASSOC_RULES = "offline:association_rules";
    public static final String CACHE_KEY_SALES_TREND = "offline:sales_trend";

    @Autowired
    private UserBehaviorMapper behaviorMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private CollaborativeFiltering collaborativeFiltering;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private DataAnalysisService dataAnalysisService;

    @Autowired
    private HybridRecommendationEngine hybridEngine;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private AnalyticsRecommendationResultMapper recommendationResultMapper;

    @Autowired
    private com.ecommerce.mapper.AnalyticsRfmUserSnapshotMapper rfmUserSnapshotMapper;

    @Autowired
    private com.ecommerce.mapper.AnalyticsRfmSegmentSnapshotMapper rfmSegmentSnapshotMapper;

    @Autowired
    private com.ecommerce.mapper.AnalyticsAssociationRuleMapper associationRuleMapper;

    @Autowired
    private com.ecommerce.mapper.AnalyticsSalesDailyMapper salesDailyMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${offline.active-user-limit:500}")
    private int activeUserLimit;

    @Value("${offline.similarity-threshold:0.01}")
    private double similarityThreshold;

    @Value("${offline.cooccurrence-min-count:2}")
    private int cooccurrenceMinCount;

    @Value("${offline.max-products-per-user:100}")
    private int maxProductsPerUser;

    /**
     * 批处理主任务 - 每小时执行
     */
    @Scheduled(fixedRate = 3600000)
    public void batchProcessBehaviors() {
        log.info("[OfflineDataProcessor] 开始批处理用户行为数据...");
        long startTime = System.currentTimeMillis();
        try {
            List<Map<String, Object>> behaviorStats = behaviorMapper.selectBehaviorStats();
            log.info("[OfflineDataProcessor] 行为数据统计: {}", behaviorStats);

            Map<String, Double> userSimMatrix = computeUserSimilarityMatrix();
            log.info("[OfflineDataProcessor] 用户相似度矩阵: {} 对", userSimMatrix.size());

            Map<String, Integer> cooccurMatrix = computeProductCooccurrenceMatrix();
            log.info("[OfflineDataProcessor] 商品共现矩阵: {} 对", cooccurMatrix.size());

            generateAllUserProfiles();

            batchRfmSegmentation();
            batchAssociationRules();
            batchSalesTrend();
            batchPersistRecommendations();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[OfflineDataProcessor] 批处理完成，耗时 {}ms", elapsed);
        } catch (Exception e) {
            log.error("[OfflineDataProcessor] 批处理异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 计算用户相似度矩阵
     *
     * 优化点：基于倒排索引（商品→用户集合）做候选对剪枝，
     * 只对至少有 1 个共同交互商品的用户对计算余弦相似度，
     * 跳过完全无交集的用户对，大幅减少实际计算量。
     */
    public Map<String, Double> computeUserSimilarityMatrix() {
        log.info("[Offline] 计算用户相似度矩阵...");
        Map<String, Double> similarityMatrix = new HashMap<>();

        Set<Long> userSet = loadActiveUserIds(activeUserLimit);

        Map<Long, Map<Long, Double>> userVectors = new HashMap<>();
        Map<Long, Set<Long>> invertedIndex = new HashMap<>();

        for (Long userId : userSet) {
            Map<Long, Double> vector = collaborativeFiltering.buildUserVector(userId);
            if (!vector.isEmpty()) {
                userVectors.put(userId, vector);
                for (Long productId : vector.keySet()) {
                    invertedIndex.computeIfAbsent(productId, k -> new HashSet<>()).add(userId);
                }
            }
        }

        Set<String> candidatePairs = new HashSet<>();
        for (Set<Long> users : invertedIndex.values()) {
            if (users.size() < 2 || users.size() > 200) continue;
            List<Long> userList = new ArrayList<>(users);
            for (int i = 0; i < userList.size(); i++) {
                for (int j = i + 1; j < userList.size(); j++) {
                    Long small = Math.min(userList.get(i), userList.get(j));
                    Long big = Math.max(userList.get(i), userList.get(j));
                    candidatePairs.add(small + ":" + big);
                }
            }
        }

        log.info("[Offline] 倒排索引剪枝: {}个用户, 候选对{}个 (vs 全量{}个)",
                userVectors.size(), candidatePairs.size(),
                (long) userVectors.size() * (userVectors.size() - 1) / 2);

        for (String pair : candidatePairs) {
            String[] parts = pair.split(":");
            Long u1 = Long.valueOf(parts[0]);
            Long u2 = Long.valueOf(parts[1]);

            Map<Long, Double> v1 = userVectors.get(u1);
            Map<Long, Double> v2 = userVectors.get(u2);
            if (v1 == null || v2 == null) continue;

            double sim = collaborativeFiltering.cosineSimilarity(v1, v2);
            if (sim > similarityThreshold) {
                similarityMatrix.put(pair, sim);
            }
        }

        try {
            for (Map.Entry<String, Double> entry : similarityMatrix.entrySet()) {
                redisUtil.set(CACHE_KEY_USER_SIM + entry.getKey(),
                        entry.getValue().toString(), 2, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("[Offline] 用户相似度矩阵写入Redis失败: {}", e.getMessage());
        }

        log.info("[Offline] 用户相似度矩阵计算完成: {} 对有效相似度", similarityMatrix.size());
        return similarityMatrix;
    }

    /**
     * 计算商品共现矩阵
     *
     * 优化点：直接从用户→商品映射遍历，以"每个用户的交互商品集"为单位生成共现对，
     * 跳过交互商品数超过 100 的超活跃用户（防止爆炸式组合），
     * 同时复用已构建的 productUserMap 计算 Jaccard 分母，避免重复集合运算。
     */
    public Map<String, Integer> computeProductCooccurrenceMatrix() {
        log.info("[Offline] 计算商品共现矩阵...");
        Map<String, Integer> cooccurrence = new HashMap<>();

        Set<Long> userSet = loadActiveUserIds(activeUserLimit);

        Map<Long, Set<Long>> userProductMap = new HashMap<>();
        for (Long userId : userSet) {
            List<Map<String, Object>> scores = behaviorMapper.selectUserProductScores(userId);
            Set<Long> products = scores.stream()
                    .map(s -> Long.valueOf(s.get("product_id").toString()))
                    .collect(Collectors.toSet());
            if (!products.isEmpty() && products.size() <= maxProductsPerUser) {
                userProductMap.put(userId, products);
            }
        }

        for (Set<Long> products : userProductMap.values()) {
            List<Long> productList = new ArrayList<>(products);
            for (int i = 0; i < productList.size(); i++) {
                for (int j = i + 1; j < productList.size(); j++) {
                    Long p1 = Math.min(productList.get(i), productList.get(j));
                    Long p2 = Math.max(productList.get(i), productList.get(j));
                    cooccurrence.merge(p1 + ":" + p2, 1, Integer::sum);
                }
            }
        }

        cooccurrence.entrySet().removeIf(entry -> entry.getValue() < cooccurrenceMinCount);

        try {
            for (Map.Entry<String, Integer> entry : cooccurrence.entrySet()) {
                redisUtil.set(CACHE_KEY_PRODUCT_COOCCUR + entry.getKey(),
                        entry.getValue().toString(), 2, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("[Offline] 商品共现矩阵写入Redis失败: {}", e.getMessage());
        }

        log.info("[Offline] 商品共现矩阵计算完成: {} 对共现关系 (共现>=2)", cooccurrence.size());
        return cooccurrence;
    }

    /**
     * 批量计算 RFM 用户分群，结果写入 Redis + MySQL
     */
    private void batchRfmSegmentation() {
        try {
            LocalDate snapshotDate = LocalDate.now();
            List<Order> paidOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>().in(Order::getStatus, 1, 2, 3));

            RfmSnapshotBundle bundle = buildRfmSnapshot(snapshotDate, paidOrders);
            redisUtil.set(CACHE_KEY_RFM,
                    com.alibaba.fastjson2.JSON.toJSONString(bundle.payload), 2, TimeUnit.HOURS);

            try {
                transactionTemplate.executeWithoutResult(status -> {
                    rfmUserSnapshotMapper.delete(new LambdaQueryWrapper<com.ecommerce.entity.AnalyticsRfmUserSnapshot>()
                            .eq(com.ecommerce.entity.AnalyticsRfmUserSnapshot::getSnapshotDate, snapshotDate));
                    rfmSegmentSnapshotMapper.delete(new LambdaQueryWrapper<com.ecommerce.entity.AnalyticsRfmSegmentSnapshot>()
                            .eq(com.ecommerce.entity.AnalyticsRfmSegmentSnapshot::getSnapshotDate, snapshotDate));

                    for (RfmUserSnapshotRecord item : bundle.userSnapshots) {
                        com.ecommerce.entity.AnalyticsRfmUserSnapshot row =
                                new com.ecommerce.entity.AnalyticsRfmUserSnapshot();
                        row.setSnapshotDate(snapshotDate);
                        row.setUserId(item.userId);
                        row.setRecencyDays(item.recencyDays);
                        row.setFrequencyCount(item.frequencyCount);
                        row.setMonetaryAmount(item.monetaryAmount);
                        row.setRScore(item.rScore);
                        row.setFScore(item.fScore);
                        row.setMScore(item.mScore);
                        row.setRfmCode(item.rfmCode);
                        row.setSegmentName(item.segmentName);
                        rfmUserSnapshotMapper.insert(row);
                    }

                    for (com.ecommerce.entity.AnalyticsRfmSegmentSnapshot row : bundle.segmentSnapshots) {
                        rfmSegmentSnapshotMapper.insert(row);
                    }
                });
            } catch (Exception e) {
                log.warn("[Offline] RFM MySQL持久化失败(不影响Redis): {}", e.getMessage());
            }

            log.info("[Offline] RFM 分群计算完成: {} 个分群",
                    bundle.payload.containsKey("segments") ? ((List<?>) bundle.payload.get("segments")).size() : 0);
        } catch (Exception e) {
            log.warn("[Offline] RFM 分群计算失败: {}", e.getMessage());
        }
    }

    private RfmSnapshotBundle buildRfmSnapshot(LocalDate snapshotDate, List<Order> paidOrders) {
        List<RfmUserSnapshotRecord> userSnapshots = buildRfmUserSnapshots(snapshotDate, paidOrders);

        List<Map<String, Object>> segmentPayload = new ArrayList<>();
        List<com.ecommerce.entity.AnalyticsRfmSegmentSnapshot> segmentSnapshots = new ArrayList<>();
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("recencyMedian", 0.0);
        thresholds.put("frequencyMedian", 0.0);
        thresholds.put("monetaryMedian", 0.0);

        if (userSnapshots.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("segments", segmentPayload);
            empty.put("details", Collections.emptyList());
            empty.put("thresholds", thresholds);
            empty.put("totalAnalyzed", 0);
            empty.put("snapshotDate", snapshotDate);
            return new RfmSnapshotBundle(empty, userSnapshots, segmentSnapshots);
        }

        double recencyMedian = median(userSnapshots.stream()
                .mapToDouble(item -> item.recencyDays)
                .toArray());
        double frequencyMedian = median(userSnapshots.stream()
                .mapToDouble(item -> item.frequencyCount)
                .toArray());
        double monetaryMedian = median(userSnapshots.stream()
                .mapToDouble(item -> item.monetaryAmount == null ? 0.0 : item.monetaryAmount.doubleValue())
                .toArray());

        thresholds.put("recencyMedian", round2(recencyMedian));
        thresholds.put("frequencyMedian", round2(frequencyMedian));
        thresholds.put("monetaryMedian", round2(monetaryMedian));

        for (RfmUserSnapshotRecord item : userSnapshots) {
            item.rScore = item.recencyDays <= recencyMedian ? 1 : 0;
            item.fScore = item.frequencyCount >= frequencyMedian ? 1 : 0;
            item.mScore = item.monetaryAmount.doubleValue() >= monetaryMedian ? 1 : 0;
            item.rfmCode = String.valueOf(item.rScore) + item.fScore + item.mScore;
            item.segmentName = rfmLabel(item.rScore, item.fScore, item.mScore);
        }

        Map<String, List<RfmUserSnapshotRecord>> bySegment = userSnapshots.stream()
                .collect(Collectors.groupingBy(item -> item.segmentName, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<RfmUserSnapshotRecord>> entry : bySegment.entrySet()) {
            String segmentName = entry.getKey();
            List<RfmUserSnapshotRecord> members = entry.getValue();
            com.ecommerce.entity.AnalyticsRfmSegmentSnapshot row =
                    new com.ecommerce.entity.AnalyticsRfmSegmentSnapshot();
            row.setSnapshotDate(snapshotDate);
            row.setSegmentName(segmentName);
            row.setUserCount((long) members.size());
            row.setPercentage(round2Decimal(members.size() * 100.0 / userSnapshots.size()));
            row.setAvgRecencyDays(round2Decimal(members.stream()
                    .mapToDouble(item -> item.recencyDays)
                    .average()
                    .orElse(0.0)));
            row.setAvgFrequency(round2Decimal(members.stream()
                    .mapToDouble(item -> item.frequencyCount)
                    .average()
                    .orElse(0.0)));
            row.setAvgMonetary(round2Decimal(members.stream()
                    .map(item -> item.monetaryAmount == null ? BigDecimal.ZERO : item.monetaryAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(Math.max(members.size(), 1L)), 2, RoundingMode.HALF_UP)
                    .doubleValue()));
            segmentSnapshots.add(row);

            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("name", segmentName);
            segment.put("count", members.size());
            segment.put("percentage", round2(members.size() * 100.0 / userSnapshots.size()));
            segmentPayload.add(segment);
        }

        List<Map<String, Object>> detailPayload = userSnapshots.stream()
                .sorted(Comparator
                        .comparing((RfmUserSnapshotRecord item) -> item.monetaryAmount, Comparator.nullsLast(BigDecimal::compareTo))
                        .reversed()
                        .thenComparing(RfmUserSnapshotRecord::getFrequencyCount, Comparator.reverseOrder())
                        .thenComparing(RfmUserSnapshotRecord::getRecencyDays))
                .limit(50)
                .map(item -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("userId", item.userId);
                    detail.put("recencyDays", item.recencyDays);
                    detail.put("frequency", item.frequencyCount);
                    detail.put("monetary", item.monetaryAmount);
                    detail.put("rScore", item.rScore);
                    detail.put("fScore", item.fScore);
                    detail.put("mScore", item.mScore);
                    detail.put("segment", item.segmentName);
                    return detail;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("segments", segmentPayload);
        result.put("details", detailPayload);
        result.put("thresholds", thresholds);
        result.put("totalAnalyzed", userSnapshots.size());
        result.put("snapshotDate", snapshotDate);
        return new RfmSnapshotBundle(result, userSnapshots, segmentSnapshots);
    }

    private List<RfmUserSnapshotRecord> buildRfmUserSnapshots(LocalDate snapshotDate, List<Order> paidOrders) {
        if (paidOrders == null || paidOrders.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<Order>> byUser = paidOrders.stream()
                .filter(order -> order.getUserId() != null)
                .collect(Collectors.groupingBy(Order::getUserId, LinkedHashMap::new, Collectors.toList()));

        List<RfmUserSnapshotRecord> results = new ArrayList<>();
        for (Map.Entry<Long, List<Order>> entry : byUser.entrySet()) {
            LocalDate lastOrderDate = entry.getValue().stream()
                    .map(this::resolvePaidOrderDate)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(snapshotDate);

            BigDecimal monetaryAmount = entry.getValue().stream()
                    .map(Order::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            RfmUserSnapshotRecord item = new RfmUserSnapshotRecord();
            item.userId = entry.getKey();
            item.recencyDays = (int) ChronoUnit.DAYS.between(lastOrderDate, snapshotDate);
            item.frequencyCount = entry.getValue().size();
            item.monetaryAmount = monetaryAmount.setScale(2, RoundingMode.HALF_UP);
            results.add(item);
        }
        return results;
    }

    private LocalDate resolvePaidOrderDate(Order order) {
        if (order == null) {
            return null;
        }
        if (order.getPayTime() != null) {
            return order.getPayTime().toLocalDate();
        }
        return order.getCreateTime() == null ? null : order.getCreateTime().toLocalDate();
    }

    private String rfmLabel(int r, int f, int m) {
        if (r == 1 && f == 1 && m == 1) return "重要价值客户";
        if (r == 1 && f == 1 && m == 0) return "重要发展客户";
        if (r == 1 && f == 0 && m == 1) return "重要保持客户";
        if (r == 1 && f == 0 && m == 0) return "新客户";
        if (r == 0 && f == 1 && m == 1) return "重要挽留客户";
        if (r == 0 && f == 1 && m == 0) return "一般客户";
        if (r == 0 && f == 0 && m == 1) return "流失高价值客户";
        return "流失客户";
    }

    /**
     * 批量挖掘关联规则，结果写入 Redis + MySQL
     */
    @SuppressWarnings("unchecked")
    private void batchAssociationRules() {
        try {
            Map<String, Object> rules = dataAnalysisService.associationRules(1, 0.1);
            redisUtil.set(CACHE_KEY_ASSOC_RULES,
                    com.alibaba.fastjson2.JSON.toJSONString(rules), 2, TimeUnit.HOURS);

            LocalDate today = LocalDate.now();
            try {
                associationRuleMapper.delete(new LambdaQueryWrapper<com.ecommerce.entity.AnalyticsAssociationRule>()
                        .eq(com.ecommerce.entity.AnalyticsAssociationRule::getSnapshotDate, today));

                Object ruleList = rules.get("rules");
                if (ruleList instanceof List) {
                    int count = 0;
                    for (Object r : (List<?>) ruleList) {
                        if (r instanceof Map && count < 200) {
                            Map<String, Object> rule = (Map<String, Object>) r;
                            com.ecommerce.entity.AnalyticsAssociationRule row = new com.ecommerce.entity.AnalyticsAssociationRule();
                            row.setSnapshotDate(today);
                            row.setLhsProductId(parseLongSafe(rule.get("productA")));
                            row.setRhsProductId(parseLongSafe(rule.get("productB")));
                            row.setSupportCount(parseLongSafe(rule.get("support")));
                            row.setSupportRate(parseDoubleSafe(rule.get("supportRate")));
                            BigDecimal confidenceAB = parseDoubleSafe(rule.get("confidenceAB"));
                            BigDecimal confidenceBA = parseDoubleSafe(rule.get("confidenceBA"));
                            BigDecimal confidence = confidenceAB == null ? confidenceBA
                                    : (confidenceBA == null ? confidenceAB : confidenceAB.max(confidenceBA));
                            row.setConfidence(confidence);
                            row.setLift(parseDoubleSafe(rule.get("lift")));
                            row.setRankNo(count + 1);
                            associationRuleMapper.insert(row);
                            count++;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[Offline] 关联规则MySQL持久化失败(不影响Redis): {}", e.getMessage());
            }

            log.info("[Offline] 关联规则挖掘完成: {} 条规则",
                    rules.containsKey("filteredRules") ? rules.get("filteredRules") : 0);
        } catch (Exception e) {
            log.warn("[Offline] 关联规则挖掘失败: {}", e.getMessage());
        }
    }

    /**
     * 批量计算销售趋势与移动平均线，结果写入 Redis + MySQL
     */
    @SuppressWarnings("unchecked")
    private void batchSalesTrend() {
        try {
            Map<String, Object> trend = dataAnalysisService.salesTrendAnalysis();
            redisUtil.set(CACHE_KEY_SALES_TREND,
                    com.alibaba.fastjson2.JSON.toJSONString(trend), 1, TimeUnit.HOURS);

            LocalDate today = LocalDate.now();
            try {
                List<?> dates = trend.get("dates") instanceof List ? (List<?>) trend.get("dates") : Collections.emptyList();
                List<?> revenues = trend.get("revenues") instanceof List ? (List<?>) trend.get("revenues") : Collections.emptyList();
                List<?> orderCounts = trend.get("orderCounts") instanceof List ? (List<?>) trend.get("orderCounts") : Collections.emptyList();
                List<?> movingAverage = trend.get("movingAverage7") instanceof List ? (List<?>) trend.get("movingAverage7") : Collections.emptyList();

                int size = Math.min(Math.min(dates.size(), revenues.size()), orderCounts.size());
                DateTimeFormatter shortFmt = DateTimeFormatter.ofPattern("MM-dd");
                for (int i = 0; i < size; i++) {
                    LocalDate date = parseMonthDayToDate(String.valueOf(dates.get(i)), today, shortFmt);
                    if (date == null) {
                        continue;
                    }
                    Long existing = salesDailyMapper.selectCount(
                            new LambdaQueryWrapper<com.ecommerce.entity.AnalyticsSalesDaily>()
                                    .eq(com.ecommerce.entity.AnalyticsSalesDaily::getStatDate, date)
                                    .eq(com.ecommerce.entity.AnalyticsSalesDaily::getIsForecast, 0));
                    if (existing != null && existing > 0) {
                        continue;
                    }

                    com.ecommerce.entity.AnalyticsSalesDaily row = new com.ecommerce.entity.AnalyticsSalesDaily();
                    row.setStatDate(date);
                    row.setIsForecast(0);
                    row.setPaidOrderCount(parseLongSafe(orderCounts.get(i)));
                    row.setRevenue(parseDoubleSafe(revenues.get(i)));
                    if (i < movingAverage.size() && movingAverage.get(i) != null) {
                        row.setMovingAvg7d(parseDoubleSafe(movingAverage.get(i)));
                    }
                    row.setModelVersion("java-fallback-v1");
                    salesDailyMapper.insert(row);
                }
            } catch (Exception e) {
                log.warn("[Offline] 销售趋势MySQL持久化失败(不影响Redis): {}", e.getMessage());
            }

            log.info("[Offline] 销售趋势计算完成: 30天数据 + 7日移动平均线");
        } catch (Exception e) {
            log.warn("[Offline] 销售趋势计算失败: {}", e.getMessage());
        }
    }

    private Long parseLongSafe(Object val) {
        if (val == null) return null;
        try { return Long.valueOf(val.toString()); } catch (Exception e) { return null; }
    }

    private BigDecimal parseDoubleSafe(Object val) {
        if (val == null) return null;
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return null; }
    }

    private BigDecimal round2Decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private double round2(double value) {
        return round2Decimal(value).doubleValue();
    }

    private double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int size = sorted.length;
        if (size == 0) {
            return 0.0;
        }
        if (size % 2 == 0) {
            return (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0;
        }
        return sorted[size / 2];
    }

    private LocalDate parseMonthDayToDate(String monthDay, LocalDate fallbackDate, DateTimeFormatter formatter) {
        if (monthDay == null || monthDay.trim().isEmpty()) {
            return null;
        }
        try {
            String[] parts = monthDay.trim().split("-");
            if (parts.length != 2) {
                return null;
            }
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            LocalDate candidate = LocalDate.of(fallbackDate.getYear(), month, day);
            if (candidate.isAfter(fallbackDate.plusDays(1))) {
                candidate = candidate.minusYears(1);
            }
            return candidate;
        } catch (Exception e) {
            try {
                return LocalDate.parse(monthDay.trim(), formatter);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /**
     * 生成每日销售统计报表
     */
    public List<Map<String, Object>> generateDailySalesReport() {
        log.info("[Offline] 生成每日销售报表...");
        return orderMapper.selectRecentStats();
    }

    /**
     * 批量生成用户画像
     */
    private void generateAllUserProfiles() {
        Set<Long> userSet = loadActiveUserIds(500);
        int count = 0;
        for (Long userId : userSet) {
            generateUserProfile(userId);
            count++;
        }
        log.info("[Offline] 用户画像生成完成: {} 个用户", count);
    }

    /**
     * 用户画像生成
     * 综合用户行为生成兴趣标签、品类偏好
     */
    public Map<String, Object> generateUserProfile(Long userId) {
        Map<String, Object> profile = new HashMap<>();

        List<Map<String, Object>> prefs = behaviorMapper.selectUserPreferences(userId);
        Map<Long, Double> categoryWeights = new HashMap<>();
        Map<String, Double> tagWeights = new HashMap<>();
        int totalBehaviors = 0;

        for (Map<String, Object> pref : prefs) {
            double weight = Double.parseDouble(pref.get("weight").toString());
            totalBehaviors += Integer.parseInt(pref.get("cnt").toString());

            Long categoryId = Long.valueOf(pref.get("category_id").toString());
            categoryWeights.merge(categoryId, weight, Double::sum);

            Object tags = pref.get("tags");
            if (tags != null) {
                String tagStr = tags.toString();
                if (tagStr.startsWith("[")) {
                    tagStr = tagStr.substring(1, tagStr.length() - 1);
                }
                for (String tag : tagStr.split(",")) {
                    tag = tag.trim().replace("\"", "");
                    if (!tag.isEmpty()) {
                        tagWeights.merge(tag, weight, Double::sum);
                    }
                }
            }
        }

        profile.put("userId", userId);
        profile.put("totalBehaviors", totalBehaviors);
        profile.put("categoryPreferences", sortMapByValue(categoryWeights));
        profile.put("tagPreferences", sortMapByValue(tagWeights));
        profile.put("isColdStart", totalBehaviors < 5);

        try {
            redisUtil.set(CACHE_KEY_USER_PROFILE + userId,
                    com.alibaba.fastjson2.JSON.toJSONString(profile), 2, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[Offline] 用户画像写入Redis失败 userId={}: {}", userId, e.getMessage());
        }

        return profile;
    }

    /**
     * 为所有活跃用户批量计算推荐结果，写入 analytics_recommendation_result 表
     */
    private void batchPersistRecommendations() {
        try {
            Set<Long> userSet = loadActiveUserIds(1000);
            LocalDate today = LocalDate.now();
            int totalRows = 0;

            if (hasPythonRecommendationSnapshot(today)) {
                log.info("[Offline] 检测到 Python 推荐快照，跳过 Java 离线推荐写入，避免覆盖新模型结果");
                return;
            }

            recommendationResultMapper.delete(
                    new LambdaQueryWrapper<AnalyticsRecommendationResult>()
                            .eq(AnalyticsRecommendationResult::getSnapshotDate, today)
                            .in(AnalyticsRecommendationResult::getScene, Arrays.asList("personal", "guess_you_like", "hot")));

            LocalDate retentionCutoff = today.minusDays(7);
            recommendationResultMapper.delete(
                    new LambdaQueryWrapper<AnalyticsRecommendationResult>()
                            .lt(AnalyticsRecommendationResult::getSnapshotDate, retentionCutoff)
                            .in(AnalyticsRecommendationResult::getScene, Arrays.asList("personal", "guess_you_like", "hot")));

            int hotRank = 1;
            for (com.ecommerce.entity.Product product : productMapper.selectHotProducts(20)) {
                AnalyticsRecommendationResult hotRow = new AnalyticsRecommendationResult();
                hotRow.setSnapshotDate(today);
                hotRow.setScene("hot");
                hotRow.setUserId(0L);
                hotRow.setProductId(product.getId());
                hotRow.setRankNo(hotRank);
                hotRow.setScore(BigDecimal.valueOf(Math.max(0.0, 1.0 - ((double) (hotRank - 1) / 20.0))).setScale(6, RoundingMode.HALF_UP));
                hotRow.setAlgorithm("hybrid_hot_fallback");
                hotRow.setReason("Fallback hot snapshot generated by Java offline processor");
                hotRow.setModelVersion("java-fallback-v1");
                recommendationResultMapper.insert(hotRow);
                totalRows++;
                hotRank++;
            }

            for (Long userId : userSet) {
                try {
                    List<Long> personalIds = hybridEngine.recommend(userId, 20);
                    totalRows += persistUserSnapshot(today, userId, "personal", personalIds, "hybrid_offline_personal", "java-fallback-v1");

                    List<Long> guessIds = hybridEngine.recommend(userId, 30);
                    Collections.shuffle(guessIds.subList(0, Math.min(10, guessIds.size())));
                    List<Long> diverseGuess = guessIds.stream().distinct().limit(20).collect(Collectors.toList());
                    totalRows += persistUserSnapshot(today, userId, "guess_you_like", diverseGuess, "hybrid_guess_diverse", "java-fallback-v1");
                } catch (Exception e) {
                    log.warn("[Offline] 用户{}推荐快照写入失败: {}", userId, e.getMessage());
                }
            }

            log.info("[Offline] 推荐快照持久化完成: {}个用户, {}条记录", userSet.size(), totalRows);
        } catch (Exception e) {
            log.warn("[Offline] 推荐快照持久化异常: {}", e.getMessage());
        }
    }

    private boolean hasPythonRecommendationSnapshot(LocalDate snapshotDate) {
        Long count = recommendationResultMapper.selectCount(
                new LambdaQueryWrapper<AnalyticsRecommendationResult>()
                        .eq(AnalyticsRecommendationResult::getSnapshotDate, snapshotDate)
                        .in(AnalyticsRecommendationResult::getScene, Arrays.asList("personal", "guess_you_like", "hot"))
                        .and(wrapper -> wrapper
                                .likeRight(AnalyticsRecommendationResult::getModelVersion, "python-rec-")
                                .or()
                                .likeRight(AnalyticsRecommendationResult::getAlgorithm, "python_"))
        );
        return count != null && count > 0;
    }

    private int persistUserSnapshot(LocalDate snapshotDate,
                                    Long userId,
                                    String scene,
                                    List<Long> productIds,
                                    String algorithm,
                                    String modelVersion) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        for (int index = 0; index < productIds.size() && index < 20; index++) {
            Long productId = productIds.get(index);
            if (productId == null) {
                continue;
            }
            double score = Math.max(0.0, 1.0 - ((double) index / 20.0));
            AnalyticsRecommendationResult row = new AnalyticsRecommendationResult();
            row.setSnapshotDate(snapshotDate);
            row.setScene(scene);
            row.setUserId(userId);
            row.setProductId(productId);
            row.setRankNo(index + 1);
            row.setScore(BigDecimal.valueOf(score).setScale(6, RoundingMode.HALF_UP));
            row.setAlgorithm(algorithm);
            row.setReason("Fallback snapshot generated by Java hybrid recommender");
            row.setModelVersion(modelVersion);
            recommendationResultMapper.insert(row);
            inserted++;
        }
        return inserted;
    }

    private <K> LinkedHashMap<K, Double> sortMapByValue(Map<K, Double> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<K, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    private Set<Long> loadActiveUserIds(int limit) {
        return new LinkedHashSet<>(behaviorMapper.selectActiveUserIds(limit));
    }

    private static class RfmUserSnapshotRecord {

        private Long userId;
        private Integer recencyDays;
        private Integer frequencyCount;
        private BigDecimal monetaryAmount;
        private Integer rScore;
        private Integer fScore;
        private Integer mScore;
        private String rfmCode;
        private String segmentName;

        private Integer getRecencyDays() {
            return recencyDays;
        }

        private Integer getFrequencyCount() {
            return frequencyCount;
        }
    }

    private static class RfmSnapshotBundle {

        private final Map<String, Object> payload;
        private final List<RfmUserSnapshotRecord> userSnapshots;
        private final List<com.ecommerce.entity.AnalyticsRfmSegmentSnapshot> segmentSnapshots;

        private RfmSnapshotBundle(Map<String, Object> payload,
                                  List<RfmUserSnapshotRecord> userSnapshots,
                                  List<com.ecommerce.entity.AnalyticsRfmSegmentSnapshot> segmentSnapshots) {
            this.payload = payload;
            this.userSnapshots = userSnapshots;
            this.segmentSnapshots = segmentSnapshots;
        }
    }
}
