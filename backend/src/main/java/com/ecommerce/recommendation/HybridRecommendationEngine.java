package com.ecommerce.recommendation;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.StreamProductHotnessRealtime;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class HybridRecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(HybridRecommendationEngine.class);

    @Autowired
    private CollaborativeFiltering collaborativeFiltering;

    @Autowired
    private ContentBasedFiltering contentBasedFiltering;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RecommendationRealtimeCacheService recommendationRealtimeCacheService;

    @Autowired
    private ABTestFramework abTestFramework;

    @Autowired
    private RecommendationExplainer explainer;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private UserPreferenceBootstrapService userPreferenceBootstrapService;

    @Value("${recommendation.collaborative-weight:0.4}")
    private double collaborativeWeight;

    @Value("${recommendation.content-weight:0.3}")
    private double contentWeight;

    @Value("${recommendation.popularity-weight:0.3}")
    private double popularityWeight;

    private static final double NOISE_FACTOR = 0.08;

    public List<Long> recommend(Long userId, int topN) {
        return recommendDetailed(userId, topN).getProductIds();
    }

    public RecommendationDecision recommendDetailed(Long userId, int topN) {
        HybridResult hybridResult = computeHybridScores(userId, topN, null);
        List<Long> result = extractTopIds(hybridResult.scoreMap, topN, hybridResult.hotIds);

        if (hybridResult.groupCode != null) {
            abTestFramework.recordExposure(userId, hybridResult.groupCode, result);
        }

        log.info("[Hybrid] user={} resultSize={} group={} cfContributed={}",
                userId,
                result.size(),
                hybridResult.groupCode != null ? hybridResult.groupCode : "disabled",
                hybridResult.cfContributed);
        return new RecommendationDecision(
                result,
                hybridResult.groupCode,
                hybridResult.weights,
                hybridResult.scoreMap,
                hybridResult.componentScoreMap,
                hybridResult.cfContributed);
    }

    public RecommendationDecision recommendDetailedForGroup(Long userId,
                                                            int topN,
                                                            ABTestFramework.ExperimentGroup forcedGroup,
                                                            boolean recordExposure) {
        HybridResult hybridResult = computeHybridScores(userId, topN, forcedGroup);
        List<Long> result = extractTopIds(hybridResult.scoreMap, topN, hybridResult.hotIds);

        if (recordExposure && hybridResult.groupCode != null) {
            abTestFramework.recordExposure(userId, hybridResult.groupCode, result);
        }

        log.info("[Hybrid] user={} forcedGroup={} resultSize={} cfContributed={}",
                userId,
                forcedGroup != null ? forcedGroup.code : "none",
                result.size(),
                hybridResult.cfContributed);

        return new RecommendationDecision(
                result,
                hybridResult.groupCode,
                hybridResult.weights,
                hybridResult.scoreMap,
                hybridResult.componentScoreMap,
                hybridResult.cfContributed);
    }

    public Map<String, Double> getDefaultWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("collaborative", collaborativeWeight);
        weights.put("content", contentWeight);
        weights.put("popularity", popularityWeight);
        return weights;
    }

    public Map<String, Object> recommendWithExplanation(Long userId, int topN) {
        HybridResult hybridResult = computeHybridScores(userId, topN, null);
        List<Long> finalIds = extractTopIds(hybridResult.scoreMap, topN, hybridResult.hotIds);

        if (hybridResult.groupCode != null) {
            abTestFramework.recordExposure(userId, hybridResult.groupCode, finalIds);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productIds", finalIds);
        result.put("experimentGroup", hybridResult.groupCode != null ? hybridResult.groupCode : "disabled");
        result.put("explanations", explainer.explain(userId, finalIds,
                hybridResult.cfResultIds, hybridResult.cbResultIds, hybridResult.hotIds));
        result.put("algorithmWeights", hybridResult.weights);
        result.put("cfContributed", hybridResult.cfContributed);
        return result;
    }

    /**
     * 核心混合评分逻辑（统一入口，消除 recommend / recommendWithExplanation 的重复代码）
     *
     * 优化点：
     * 1. 使用 CF/CB 返回的原始模型分数（归一化到 [0,1]），而非丢弃后用排名线性分替代
     * 2. 噪声缩放从 0.3×listSize 改为 0.08×score，确保噪声永远不会超过信号
     * 3. 热门商品保留销量排序，不再随机 shuffle 破坏排名
     */
    private HybridResult computeHybridScores(Long userId, int topN, ABTestFramework.ExperimentGroup forcedGroup) {
        if (userId != null && userId > 0) {
            userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        }
        boolean abTestEnabled = moduleSwitchService.isEnabled("ab-test");
        ABTestFramework.ExperimentGroup group = forcedGroup != null
                ? (abTestEnabled ? forcedGroup : null)
                : (abTestEnabled ? abTestFramework.assignGroup(userId) : null);
        Map<String, Double> weights = group != null ? abTestFramework.getWeights(group) : getDefaultWeights();

        double cfWeight = weights.getOrDefault("collaborative", collaborativeWeight);
        double cbWeight = weights.getOrDefault("content", contentWeight);
        double hotWeight = weights.getOrDefault("popularity", popularityWeight);

        log.debug("[Hybrid] user={} group={} weights: CF={}, CB={}, HOT={}",
                userId, group != null ? group.code : "disabled", cfWeight, cbWeight, hotWeight);

        Map<Long, Double> scoreMap = new HashMap<>();
        Random random = new Random(Objects.hash(userId, topN, group != null ? group.code : "default"));
        Map<Long, Map<String, Double>> componentScoreMap = new HashMap<>();

        Map<Long, Double> cfScores = Collections.emptyMap();
        List<Long> cfResultIds = Collections.emptyList();
        if (cfWeight > 0) {
            cfScores = collaborativeFiltering.userBasedRecommendWithScores(userId, topN * 2);
            cfResultIds = new ArrayList<>(cfScores.keySet());
        }

        double effectiveCfWeight = cfWeight;
        double effectiveCbWeight = cbWeight;
        double effectiveHotWeight = hotWeight;
        if (cfScores.isEmpty() && cbWeight > 0) {
            effectiveCbWeight = cbWeight + cfWeight * 0.7;
            effectiveHotWeight = hotWeight + cfWeight * 0.3;
            effectiveCfWeight = 0;
            log.info("[Hybrid] user={} CF empty, redistributed weights: CB={}, HOT={}",
                    userId, effectiveCbWeight, effectiveHotWeight);
        }

        for (Map.Entry<Long, Double> entry : cfScores.entrySet()) {
            double baseScore = entry.getValue() * effectiveCfWeight;
            double noise = random.nextDouble() * NOISE_FACTOR * baseScore;
            addScoreComponent(componentScoreMap, entry.getKey(), "collaborativeRaw", entry.getValue());
            addScoreComponent(componentScoreMap, entry.getKey(), "collaborative", baseScore);
            addScoreComponent(componentScoreMap, entry.getKey(), "noise", noise);
            scoreMap.merge(entry.getKey(), baseScore + noise, Double::sum);
        }

        Map<Long, Double> cbScores = Collections.emptyMap();
        List<Long> cbResultIds = Collections.emptyList();
        if (effectiveCbWeight > 0) {
            cbScores = contentBasedFiltering.recommendWithScores(userId, topN * 2);
            cbResultIds = new ArrayList<>(cbScores.keySet());
            for (Map.Entry<Long, Double> entry : cbScores.entrySet()) {
                double baseScore = entry.getValue() * effectiveCbWeight;
                double noise = random.nextDouble() * NOISE_FACTOR * baseScore;
                addScoreComponent(componentScoreMap, entry.getKey(), "contentRaw", entry.getValue());
                addScoreComponent(componentScoreMap, entry.getKey(), "content", baseScore);
                addScoreComponent(componentScoreMap, entry.getKey(), "noise", noise);
                scoreMap.merge(entry.getKey(), baseScore + noise, Double::sum);
            }
        }
        if (cbScores.isEmpty() && effectiveCbWeight > 0D) {
            if (cfScores.isEmpty()) {
                effectiveHotWeight += effectiveCbWeight;
                log.info("[Hybrid] user={} CF/CB empty, all remaining weight moved to HOT={}",
                        userId, effectiveHotWeight);
            } else {
                effectiveHotWeight += effectiveCbWeight * 0.35;
                effectiveCfWeight += effectiveCbWeight * 0.65;
                log.info("[Hybrid] user={} CB empty, redistributed weights: CF={}, HOT={}",
                        userId, effectiveCfWeight, effectiveHotWeight);
            }
        }

        int hotCandidatePool = Math.max(topN * Constants.Recommendation.HOT_CANDIDATE_POOL_MULTIPLIER, 
                Constants.Recommendation.MIN_HOT_CANDIDATE_POOL);
        List<StreamProductHotnessRealtime> realtimeHotRows = loadRealtimeHotRows(hotCandidatePool);
        List<Long> hotIds = new ArrayList<>();
        if (!realtimeHotRows.isEmpty()) {
            int hotLimit = Math.min(realtimeHotRows.size(), topN * 2);
            Map<Long, Double> hotScoreByProduct = new HashMap<>();
            List<Long> rawHotIds = new ArrayList<>();
            for (int i = 0; i < hotLimit; i++) {
                StreamProductHotnessRealtime row = realtimeHotRows.get(i);
                if (row == null || row.getProductId() == null) {
                    continue;
                }
                rawHotIds.add(row.getProductId());
                hotScoreByProduct.put(row.getProductId(), row.getHotScore() == null ? 0D : row.getHotScore());
            }
            hotIds = diversifyHotIdsByCategory(rawHotIds, hotLimit);
            if (hotIds.isEmpty()) {
                hotIds = rawHotIds;
            }
            double maxHotScore = realtimeHotRows.stream()
                    .map(StreamProductHotnessRealtime::getHotScore)
                    .filter(Objects::nonNull)
                    .max(Double::compareTo)
                    .orElse(0D);
            int rerankedHotLimit = hotIds.size();
            for (int i = 0; i < rerankedHotLimit; i++) {
                Long productId = hotIds.get(i);
                if (productId == null) {
                    continue;
                }
                double rowHotScore = hotScoreByProduct.getOrDefault(productId, 0D);
                double normalizedHotScore = maxHotScore > 0D ? rowHotScore / maxHotScore : 0D;
                double rankScore = (double) (rerankedHotLimit - i) / Math.max(rerankedHotLimit, 1);
                double baseScore = Math.max(normalizedHotScore, rankScore * 0.6) * effectiveHotWeight;
                double noise = random.nextDouble() * NOISE_FACTOR * baseScore;
                addScoreComponent(componentScoreMap, productId, "hotRaw", normalizedHotScore);
                addScoreComponent(componentScoreMap, productId, "hotRank", rankScore);
                addScoreComponent(componentScoreMap, productId, "hot", baseScore);
                addScoreComponent(componentScoreMap, productId, "noise", noise);
                scoreMap.merge(productId, baseScore + noise, Double::sum);
            }
            log.debug("[Hybrid] user={} hot candidates from stream_product_hotness_realtime size={}",
                    userId, hotIds.size());
        } else {
            List<Product> hotProducts = productMapper.selectHotProducts(hotCandidatePool);
            int hotLimit = Math.min(hotProducts.size(), topN * 2);
            List<Product> hotProductPool = new ArrayList<>();
            for (int i = 0; i < hotLimit; i++) {
                Product product = hotProducts.get(i);
                if (product != null && product.getId() != null) {
                    hotProductPool.add(product);
                }
            }
            hotProductPool = diversifyProductsByCategory(hotProductPool, hotLimit);
            hotIds = hotProductPool.stream().map(Product::getId).filter(Objects::nonNull).collect(Collectors.toList());
            int rerankedHotLimit = hotIds.size();
            for (int i = 0; i < rerankedHotLimit; i++) {
                Long productId = hotIds.get(i);
                if (productId == null) {
                    continue;
                }
                double rankScore = (double) (rerankedHotLimit - i) / Math.max(rerankedHotLimit, 1);
                double baseScore = rankScore * effectiveHotWeight;
                double noise = random.nextDouble() * NOISE_FACTOR * baseScore;
                addScoreComponent(componentScoreMap, productId, "hotRank", rankScore);
                addScoreComponent(componentScoreMap, productId, "hot", baseScore);
                addScoreComponent(componentScoreMap, productId, "noise", noise);
                scoreMap.merge(productId, baseScore + noise, Double::sum);
            }
        }

        HybridResult hybridResult = new HybridResult();
        hybridResult.scoreMap = scoreMap;
        hybridResult.cfResultIds = cfResultIds;
        hybridResult.cbResultIds = cbResultIds;
        hybridResult.hotIds = hotIds;
        hybridResult.componentScoreMap = componentScoreMap;
        hybridResult.groupCode = group != null ? group.code : null;
        hybridResult.weights = weights;
        hybridResult.cfContributed = effectiveCfWeight > 0D && !cfScores.isEmpty();
        return hybridResult;
    }

    private void addScoreComponent(Map<Long, Map<String, Double>> componentScoreMap,
                                   Long productId,
                                   String key,
                                   double value) {
        if (productId == null || key == null) {
            return;
        }
        componentScoreMap.computeIfAbsent(productId, ignored -> new LinkedHashMap<>())
                .merge(key, value, Double::sum);
    }

    private List<Long> extractTopIds(Map<Long, Double> scoreMap, int topN, List<Long> fallbackIds) {
        if (scoreMap.isEmpty()) {
            return fallbackIds.stream().limit(topN).collect(Collectors.toList());
        }
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<StreamProductHotnessRealtime> loadRealtimeHotRows(int limit) {
        return recommendationRealtimeCacheService.getHotRows(limit);
    }

    private List<Long> diversifyHotIdsByCategory(List<Long> hotIds, int targetSize) {
        if (hotIds == null || hotIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Product> products = productMapper.selectByIds(hotIds);
        if (products == null || products.isEmpty()) {
            return hotIds.stream().filter(Objects::nonNull).limit(Math.max(1, targetSize)).collect(Collectors.toList());
        }
        Map<Long, Product> productMap = products.stream()
                .filter(Objects::nonNull)
                .filter(product -> product.getId() != null)
                .collect(Collectors.toMap(Product::getId, product -> product, (left, right) -> left, LinkedHashMap::new));
        List<Product> orderedProducts = new ArrayList<>();
        for (Long id : hotIds) {
            Product product = productMap.get(id);
            if (product != null) {
                orderedProducts.add(product);
            }
        }
        List<Product> diversified = diversifyProductsByCategory(orderedProducts, targetSize);
        return diversified.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Product> diversifyProductsByCategory(List<Product> products, int targetSize) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        int safeTargetSize = Math.max(1, targetSize);
        LinkedHashMap<Long, Product> unique = new LinkedHashMap<>();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            unique.putIfAbsent(product.getId(), product);
        }
        Map<String, Deque<Product>> byCategory = new LinkedHashMap<>();
        for (Product product : unique.values()) {
            byCategory.computeIfAbsent(resolveCategoryKey(product), ignored -> new ArrayDeque<>()).add(product);
        }

        List<Product> diversified = new ArrayList<>();
        while (diversified.size() < safeTargetSize) {
            boolean hasMore = false;
            for (Deque<Product> queue : byCategory.values()) {
                if (queue == null || queue.isEmpty()) {
                    continue;
                }
                hasMore = true;
                diversified.add(queue.pollFirst());
                if (diversified.size() >= safeTargetSize) {
                    break;
                }
            }
            if (!hasMore) {
                break;
            }
        }
        return diversified;
    }

    private String resolveCategoryKey(Product product) {
        if (product == null) {
            return "category:unknown";
        }
        if (product.getCategoryId() != null) {
            return "category:id:" + product.getCategoryId();
        }
        if (product.getCategoryName() != null && !product.getCategoryName().trim().isEmpty()) {
            return "category:name:" + product.getCategoryName().trim();
        }
        return "category:unknown";
    }

    private static class HybridResult {
        Map<Long, Double> scoreMap = Collections.emptyMap();
        Map<Long, Map<String, Double>> componentScoreMap = Collections.emptyMap();
        List<Long> cfResultIds = Collections.emptyList();
        List<Long> cbResultIds = Collections.emptyList();
        List<Long> hotIds = Collections.emptyList();
        String groupCode;
        Map<String, Double> weights;
        boolean cfContributed;
    }

    public static class RecommendationDecision {
        private final List<Long> productIds;
        private final String experimentGroup;
        private final Map<String, Double> algorithmWeights;
        private final Map<Long, Double> scoreMap;
        private final Map<Long, Map<String, Double>> componentScoreMap;
        private final boolean cfContributed;

        public RecommendationDecision(List<Long> productIds,
                                      String experimentGroup,
                                      Map<String, Double> algorithmWeights,
                                      boolean cfContributed) {
            this(productIds, experimentGroup, algorithmWeights, Collections.emptyMap(), Collections.emptyMap(), cfContributed);
        }

        public RecommendationDecision(List<Long> productIds,
                                      String experimentGroup,
                                      Map<String, Double> algorithmWeights,
                                      Map<Long, Double> scoreMap,
                                      Map<Long, Map<String, Double>> componentScoreMap,
                                      boolean cfContributed) {
            this.productIds = productIds == null ? Collections.emptyList() : productIds;
            this.experimentGroup = experimentGroup;
            this.algorithmWeights = algorithmWeights == null ? Collections.emptyMap() : algorithmWeights;
            this.scoreMap = scoreMap == null ? Collections.emptyMap() : scoreMap;
            this.componentScoreMap = componentScoreMap == null ? Collections.emptyMap() : componentScoreMap;
            this.cfContributed = cfContributed;
        }

        public List<Long> getProductIds() {
            return productIds;
        }

        public String getExperimentGroup() {
            return experimentGroup;
        }

        public Map<String, Double> getAlgorithmWeights() {
            return algorithmWeights;
        }

        public Map<Long, Double> getScoreMap() {
            return scoreMap;
        }

        public Map<Long, Map<String, Double>> getComponentScoreMap() {
            return componentScoreMap;
        }

        public boolean isCfContributed() {
            return cfContributed;
        }
    }

    public List<Long> findSimilar(Long productId, int topN) {
        List<Long> contentSimilar = contentBasedFiltering.findSimilarProducts(productId, topN);
        if (contentSimilar.size() >= topN) {
            return contentSimilar;
        }

        List<Long> cfSimilar = collaborativeFiltering.itemBasedRecommend(productId, topN - contentSimilar.size());
        Set<Long> result = new LinkedHashSet<>(contentSimilar);
        result.addAll(cfSimilar);
        return new ArrayList<>(result).subList(0, Math.min(result.size(), topN));
    }
}
