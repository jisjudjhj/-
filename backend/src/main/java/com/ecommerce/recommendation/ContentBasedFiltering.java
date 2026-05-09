package com.ecommerce.recommendation;

import com.ecommerce.util.InterestTagTaxonomy;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.StreamUserCategoryPreference;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.entity.UserPreference;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.mapper.UserPreferenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于内容的推荐算法
 *
 * 核心思想: 通过分析用户历史行为构建兴趣画像，
 *          然后用Jaccard相似度匹配商品特征与用户画像的相似程度
 *
 * 特征维度:
 * - 标签特征: 商品tags字段 (如["华为","旗舰","5G"])
 * - 品类特征: 商品所属分类
 * - 描述特征: 从商品描述中提取关键词 (简化TF-IDF)
 *
 * 相似度计算:
 * - Jaccard相似度: J(A,B) = |A ∩ B| / |A ∪ B|
 * - 综合得分: 0.5 * tagSim + 0.3 * categorySim + 0.2 * priceSim
 */
@Component
public class ContentBasedFiltering {

    private static final Logger log = LoggerFactory.getLogger(ContentBasedFiltering.class);

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserBehaviorMapper behaviorMapper;

    @Autowired
    private RecommendationRealtimeCacheService recommendationRealtimeCacheService;

    @Autowired
    private UserPreferenceMapper userPreferenceMapper;

    @Autowired
    private UserPreferenceBootstrapService userPreferenceBootstrapService;

    /**
     * 基于内容推荐: 返回有序商品ID列表（兼容旧接口）
     */
    public List<Long> recommend(Long userId, int topN) {
        return recommendWithScores(userId, topN).entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 基于内容推荐: 返回 productId -> score，供混合引擎使用原始分数
     *
     * 优化点：
     * 1. 用户标签带权重（行为权重累计），而非简单集合
     * 2. 新增价格区间偏好，在评分中引入价格匹配度
     * 3. 扩大品类候选范围至 8 个，避免遗漏长尾偏好
     */
    public Map<Long, Double> recommendWithScores(Long userId, int topN) {
        int safeTopN = Math.max(1, topN);
        if (userId != null && userId > 0) {
            userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        }
        Map<Long, Double> realtimeScores = recommendFromStreamPreferences(userId, safeTopN);
        if (!realtimeScores.isEmpty()) {
            return realtimeScores;
        }

        List<Map<String, Object>> preferences = behaviorMapper.selectUserPreferences(userId);
        List<Map<String, Object>> searchCategoryPreferences = loadSearchCategoryPreferences(userId, 8);
        if (preferences.isEmpty() && searchCategoryPreferences.isEmpty()) {
            return recommendFromSavedPreferences(userId, safeTopN);
        }

        Map<Long, Double> categoryWeights = new HashMap<>();
        Map<String, Double> tagWeights = new HashMap<>();
        Set<Long> viewedProducts = new HashSet<>();
        Set<Long> purchasedProductIds = new HashSet<>();
        double totalWeight = 0;
        double priceSum = 0;
        int priceCount = 0;

        for (Map<String, Object> pref : preferences) {
            Long categoryId = Long.valueOf(pref.get("category_id").toString());
            double weight = Double.parseDouble(pref.get("weight").toString());
            categoryWeights.merge(categoryId, weight, Double::sum);
            totalWeight += weight;

            Long productId = Long.valueOf(pref.get("product_id").toString());
            viewedProducts.add(productId);

            long purchaseCount = readLong(pref.get("purchase_count"));
            if (purchaseCount > 0) {
                purchasedProductIds.add(productId);
            }

            Object tags = pref.get("tags");
            if (tags != null) {
                Set<String> parsedTags = parseTags(tags.toString());
                for (String tag : parsedTags) {
                    tagWeights.merge(tag, weight, Double::sum);
                }
            }
        }

        for (Map<String, Object> pref : searchCategoryPreferences) {
            Long categoryId = readLongObject(pref.get("category_id"));
            double weight = readDouble(pref.get("weight"));
            if (categoryId == null || weight <= 0D) {
                continue;
            }
            categoryWeights.merge(categoryId, weight, Double::sum);
            totalWeight += weight;
        }

        if (totalWeight > 0D) {
            for (Map.Entry<Long, Double> entry : categoryWeights.entrySet()) {
                entry.setValue(entry.getValue() / totalWeight);
            }
        }

        double tagTotalWeight = tagWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (tagTotalWeight > 0) {
            tagWeights.replaceAll((k, v) -> v / tagTotalWeight);
        }

        UserProfile profile = new UserProfile();
        profile.tags = tagWeights.keySet();
        profile.tagWeights = tagWeights;
        profile.categoryWeights = categoryWeights;

        if (!viewedProducts.isEmpty()) {
            List<Product> viewedProductList = productMapper.selectBatchIds(new ArrayList<>(viewedProducts));
            for (Product p : viewedProductList) {
                if (p.getPrice() != null) {
                    priceSum += p.getPrice().doubleValue();
                    priceCount++;
                }
            }
        }
        profile.avgPrice = priceCount > 0 ? priceSum / priceCount : 0;

        boolean sparseUser = preferences.size() + searchCategoryPreferences.size() < 10;
        Set<Long> excludedProductIds = purchasedProductIds.isEmpty() ? viewedProducts : purchasedProductIds;

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        wrapper.notIn(Product::getId, excludedProductIds);

        Set<Long> topCategories = categoryWeights.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(sparseUser ? 3 : 8)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!topCategories.isEmpty() && (!sparseUser || hasStrongCategorySignal(categoryWeights))) {
            wrapper.in(Product::getCategoryId, topCategories);
        }
        wrapper.last("LIMIT " + topN * 5);

        List<Product> candidates = productMapper.selectList(wrapper);

        Map<Long, Double> productScores = new HashMap<>();
        for (Product product : candidates) {
            double score = calculateContentScore(product, profile);
            if (score > 0) {
                productScores.put(product.getId(), score);
            }
        }

        log.debug("[Content-CB] 用户{} 行为数{} 画像标签{}, 候选{}个, 有效{}个",
                userId, preferences.size() + searchCategoryPreferences.size(), tagWeights.size(), candidates.size(), productScores.size());
        return finalizeScoresWithFallback(userId, safeTopN, profile, excludedProductIds, productScores, "behavior_profile");
    }

    private List<Map<String, Object>> loadSearchCategoryPreferences(Long userId, int limit) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> rows = behaviorMapper.selectUserSearchCategoryPreferences(userId, Math.max(1, limit));
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception exception) {
            log.debug("[Content-CB] 搜索词类目画像读取失败 userId={}: {}", userId, exception.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<Long, Double> recommendFromSavedPreferences(Long userId, int topN) {
        int safeTopN = Math.max(1, topN);
        UserPreference savedPref = loadSavedPreference(userId);
        if (savedPref == null || savedPref.getCategoryPreferences() == null
                || savedPref.getCategoryPreferences().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> catPrefs = savedPref.getCategoryPreferences();
        double total = catPrefs.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return Collections.emptyMap();

        Map<Long, Double> categoryWeights = new HashMap<>();
        for (Map.Entry<String, Integer> entry : catPrefs.entrySet()) {
            try {
                categoryWeights.put(Long.valueOf(entry.getKey()), entry.getValue() / total);
            } catch (NumberFormatException ignored) {}
        }

        UserProfile profile = new UserProfile();
        profile.categoryWeights = categoryWeights;

        if (savedPref.getTagPreferences() != null) {
            Map<String, Double> tagW = new HashMap<>();
            double tagTotal = savedPref.getTagPreferences().values().stream().mapToInt(Integer::intValue).sum();
            if (tagTotal > 0) {
                for (Map.Entry<String, Integer> e : savedPref.getTagPreferences().entrySet()) {
                    tagW.put(e.getKey(), e.getValue() / tagTotal);
                }
            }
            profile.tagWeights = tagW;
            profile.tags = tagW.keySet();
        }

        if (savedPref.getPriceRangeMin() != null && savedPref.getPriceRangeMax() != null) {
            profile.avgPrice = savedPref.getPriceRangeMin()
                    .add(savedPref.getPriceRangeMax())
                    .divide(java.math.BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP)
                    .doubleValue();
        }

        Set<Long> catIds = categoryWeights.keySet();
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (!catIds.isEmpty()) {
            wrapper.in(Product::getCategoryId, catIds);
        }
        wrapper.last("LIMIT " + safeTopN * 5);

        List<Product> candidates = productMapper.selectList(wrapper);
        Map<Long, Double> scores = new HashMap<>();
        for (Product p : candidates) {
            double score = calculateContentScore(p, profile);
            if (score > 0) scores.put(p.getId(), score);
        }
        log.info("[Content-CB] 冷启动用户{} 从user_preference构建画像, 候选{}个", userId, scores.size());
        return finalizeScoresWithFallback(userId, safeTopN, profile, Collections.emptySet(), scores, "saved_profile");
    }

    private Map<Long, Double> recommendFromStreamPreferences(Long userId, int topN) {
        int safeTopN = Math.max(1, topN);
        if (userId == null || userId <= 0) {
            return Collections.emptyMap();
        }

        int realtimeLimit = Math.max(safeTopN * 4, 24);
        List<StreamUserCategoryPreference> realtimePreferences =
                recommendationRealtimeCacheService.getUserPreferenceRows(userId, realtimeLimit);
        if (realtimePreferences == null || realtimePreferences.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Double> categoryWeights = new LinkedHashMap<>();
        double total = 0D;
        for (StreamUserCategoryPreference preference : realtimePreferences) {
            if (preference == null || preference.getCategoryId() == null) {
                continue;
            }
            double score = preference.getPreferenceScore() == null ? 0D : preference.getPreferenceScore();
            if (score <= 0D) {
                continue;
            }
            categoryWeights.merge(preference.getCategoryId(), score, Double::sum);
            total += score;
        }
        if (categoryWeights.isEmpty() || total <= 0D) {
            return Collections.emptyMap();
        }
        final double totalWeight = total;
        categoryWeights.replaceAll((categoryId, score) -> score / totalWeight);

        UserProfile profile = new UserProfile();
        profile.categoryWeights = categoryWeights;
        enrichProfileWithSavedPreference(profile, loadSavedPreference(userId));

        Set<Long> excludedProductIds = loadExcludedProductIds(userId);
        Set<Long> topCategories = categoryWeights.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (!excludedProductIds.isEmpty()) {
            wrapper.notIn(Product::getId, excludedProductIds);
        }
        if (!topCategories.isEmpty()) {
            wrapper.in(Product::getCategoryId, topCategories);
        }
        wrapper.last("LIMIT " + Math.max(safeTopN * 6, 36));

        List<Product> candidates = productMapper.selectList(wrapper);
        if (candidates == null || candidates.isEmpty()) {
            return finalizeScoresWithFallback(userId, safeTopN, profile, excludedProductIds, Collections.emptyMap(), "stream_candidates_empty");
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        for (Product product : candidates) {
            double score = calculateContentScore(product, profile);
            if (score > 0D) {
                scores.put(product.getId(), score);
            }
        }
        log.info("[Content-CB] 用户{} 优先从stream_user_category_preference构建画像, 实时类目{}个, 候选{}个, 有效{}个",
                userId, categoryWeights.size(), candidates.size(), scores.size());
        return finalizeScoresWithFallback(userId, safeTopN, profile, excludedProductIds, scores, "stream_profile");
    }

    private UserPreference loadSavedPreference(Long userId) {
        return userPreferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, userId)
                        .last("LIMIT 1"));
    }

    private void enrichProfileWithSavedPreference(UserProfile profile, UserPreference savedPref) {
        if (profile == null || savedPref == null) {
            return;
        }

        if (savedPref.getTagPreferences() != null && !savedPref.getTagPreferences().isEmpty()) {
            Map<String, Double> tagWeights = new HashMap<>();
            double tagTotal = savedPref.getTagPreferences().values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            if (tagTotal > 0D) {
                for (Map.Entry<String, Integer> entry : savedPref.getTagPreferences().entrySet()) {
                    tagWeights.put(entry.getKey(), entry.getValue() / tagTotal);
                }
                profile.tagWeights = tagWeights;
                profile.tags = tagWeights.keySet();
            }
        }

        if (savedPref.getPriceRangeMin() != null && savedPref.getPriceRangeMax() != null) {
            profile.avgPrice = savedPref.getPriceRangeMin()
                    .add(savedPref.getPriceRangeMax())
                    .divide(java.math.BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }

    private Set<Long> loadExcludedProductIds(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptySet();
        }
        List<UserBehavior> behaviors = behaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .select(UserBehavior::getProductId)
                        .eq(UserBehavior::getUserId, userId)
                        .isNotNull(UserBehavior::getProductId)
                        .orderByDesc(UserBehavior::getCreateTime)
                        .last("LIMIT 200"));
        if (behaviors == null || behaviors.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> productIds = new LinkedHashSet<>();
        for (UserBehavior behavior : behaviors) {
            if (behavior != null && behavior.getProductId() != null) {
                productIds.add(behavior.getProductId());
            }
        }
        return productIds;
    }

    /**
     * 基于内容特征查找相似商品
     * 使用目标商品的标签和品类作为查询画像，对所有同品类商品计算Jaccard相似度
     */
    public List<Long> findSimilarProducts(Long productId, int topN) {
        Product target = productMapper.selectById(productId);
        if (target == null) {
            return Collections.emptyList();
        }

        Set<String> targetTags = parseTags(target.getTags() != null ? target.getTags().toString() : "");

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        wrapper.ne(Product::getId, productId);
        wrapper.last("LIMIT " + topN * 10);

        List<Product> candidates = productMapper.selectList(wrapper);

        Map<Long, Double> scores = new HashMap<>();
        for (Product candidate : candidates) {
            double score = 0;

            Set<String> candidateTags = parseTags(
                    candidate.getTags() != null ? candidate.getTags().toString() : "");
            double tagSim = jaccardSimilarity(targetTags, candidateTags);
            score += tagSim * 0.6;

            if (Objects.equals(target.getCategoryId(), candidate.getCategoryId())) {
                score += 0.3;
            }

            if (target.getPrice() != null && candidate.getPrice() != null) {
                double priceDiff = Math.abs(target.getPrice().doubleValue() - candidate.getPrice().doubleValue());
                double maxPrice = Math.max(target.getPrice().doubleValue(), candidate.getPrice().doubleValue());
                if (maxPrice > 0) {
                    score += (1 - priceDiff / maxPrice) * 0.1;
                }
            }

            if (score > 0.05) {
                scores.put(candidate.getId(), score);
            }
        }

        List<Long> result = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.debug("[Content-CB] 商品{} 标签{}, 找到{}个相似商品",
                productId, targetTags, result.size());
        return result;
    }

    /**
     * 计算商品与用户画像的综合内容得分
     * 综合得分 = 0.30 * weightedTagSim + 0.45 * categoryWeight + 0.10 * priceMatch + 0.08 * salesNorm + 0.07 * ratingNorm
     *
     * 优化点：类目偏好必须是内容推荐主信号，销量和评分只做同类商品内的质量排序。
     */
    private double calculateContentScore(Product product, UserProfile profile) {
        double score = 0;

        Set<String> productTags = parseTags(
                product.getTags() != null ? product.getTags().toString() : "");

        if (profile.tagWeights != null && !profile.tagWeights.isEmpty() && !productTags.isEmpty()) {
            double weightedHit = 0;
            for (String pTag : productTags) {
                Double w = profile.tagWeights.get(pTag);
                if (w != null) {
                    weightedHit += w;
                }
            }
            double tagSim = Math.min(1.0, weightedHit * 2);
            score += Math.max(tagSim, InterestTagTaxonomy.weightedOverlap(profile.tags, productTags)) * 0.30;
        } else {
            double tagSim = jaccardSimilarity(profile.tags, productTags);
            score += tagSim * 0.30;
        }

        Double catWeight = profile.categoryWeights.getOrDefault(product.getCategoryId(), 0.0);
        score += catWeight * 0.45;

        if (profile.avgPrice > 0 && product.getPrice() != null) {
            double priceDiff = Math.abs(product.getPrice().doubleValue() - profile.avgPrice);
            double priceMatch = Math.max(0, 1.0 - priceDiff / Math.max(profile.avgPrice, 1.0));
            score += priceMatch * 0.10;
        }

        if (product.getSalesCount() != null && product.getSalesCount() > 0) {
            double salesNorm = Math.min(1.0, Math.log10(product.getSalesCount() + 1) / 4.0);
            score += salesNorm * 0.08;
        }

        if (product.getRating() != null) {
            double ratingNorm = product.getRating().doubleValue() / 5.0;
            score += ratingNorm * 0.07;
        }

        return score;
    }

    private boolean hasStrongCategorySignal(Map<Long, Double> categoryWeights) {
        if (categoryWeights == null || categoryWeights.isEmpty()) {
            return false;
        }
        double maxWeight = categoryWeights.values().stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0D);
        return maxWeight >= 0.42D || categoryWeights.size() <= 2;
    }

    /**
     * Jaccard相似度
     * J(A,B) = |A ∩ B| / |A ∪ B|
     */
    public double jaccardSimilarity(Set<String> setA, Set<String> setB) {
        if (setA.isEmpty() && setB.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return (double) intersection.size() / union.size();
    }

    private Set<String> parseTags(String tagStr) {
        Set<String> tags = new HashSet<>();
        if (tagStr == null || tagStr.isEmpty()) return tags;
        if (tagStr.startsWith("[")) {
            tagStr = tagStr.substring(1, tagStr.length() - 1);
        }
        for (String tag : tagStr.split(",")) {
            tag = tag.trim().replace("\"", "").replace("'", "");
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
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
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Long readLongObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private double readDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private Map<Long, Double> finalizeScoresWithFallback(Long userId,
                                                         int topN,
                                                         UserProfile profile,
                                                         Set<Long> excludedProductIds,
                                                         Map<Long, Double> primaryScores,
                                                         String reason) {
        Map<Long, Double> mergedScores = new LinkedHashMap<>();
        if (primaryScores != null && !primaryScores.isEmpty()) {
            mergedScores.putAll(primaryScores);
        }

        Map<Long, Double> fallbackScores = Collections.emptyMap();
        boolean needFallback = mergedScores.isEmpty() || mergedScores.size() < Math.max(topN, 6);
        if (needFallback) {
            fallbackScores = buildHotFallbackScores(profile, excludedProductIds, topN);
            for (Map.Entry<Long, Double> entry : fallbackScores.entrySet()) {
                mergedScores.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        Map<Long, Double> normalized = sortByScoreDesc(normalizeScores(mergedScores));
        if (normalized.isEmpty() && !fallbackScores.isEmpty()) {
            normalized = sortByScoreDesc(normalizeScores(fallbackScores));
        }
        if (needFallback && !normalized.isEmpty()) {
            log.info("[Content-CB] 用户{} 触发召回降级链路 reason={}, output={}",
                    userId, reason, normalized.size());
        }
        return normalized;
    }

    private Map<Long, Double> buildHotFallbackScores(UserProfile profile,
                                                     Set<Long> excludedProductIds,
                                                     int topN) {
        int safeTopN = Math.max(1, topN);
        List<Product> hotProducts = productMapper.selectHotProducts(Math.max(safeTopN * 6, 48));
        if (hotProducts == null || hotProducts.isEmpty()) {
            return Collections.emptyMap();
        }
        hotProducts = diversifyProductsByCategory(hotProducts, Math.max(safeTopN * 6, 48));
        Set<Long> excluded = excludedProductIds == null ? Collections.emptySet() : excludedProductIds;
        Map<Long, Double> scores = scoreFallbackHotProducts(hotProducts, profile, excluded, safeTopN);
        if (scores.isEmpty() && !excluded.isEmpty()) {
            scores = scoreFallbackHotProducts(hotProducts, profile, Collections.emptySet(), safeTopN);
        }
        return scores;
    }

    private List<Product> diversifyProductsByCategory(List<Product> source, int targetSize) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        int safeTargetSize = Math.max(1, targetSize);
        LinkedHashMap<Long, Product> unique = new LinkedHashMap<>();
        for (Product product : source) {
            if (product == null || product.getId() == null) {
                continue;
            }
            unique.putIfAbsent(product.getId(), product);
        }
        List<Product> deduplicated = new ArrayList<>(unique.values());
        if (deduplicated.size() <= 2) {
            return deduplicated;
        }

        Map<String, Deque<Product>> byCategory = new LinkedHashMap<>();
        for (Product product : deduplicated) {
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
        if (diversified.isEmpty()) {
            return deduplicated;
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

    private Map<Long, Double> scoreFallbackHotProducts(List<Product> hotProducts,
                                                       UserProfile profile,
                                                       Set<Long> excluded,
                                                       int topN) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        int total = hotProducts.size();
        int maxKeep = Math.max(topN * 4, 24);
        for (int i = 0; i < hotProducts.size(); i++) {
            Product product = hotProducts.get(i);
            if (product == null || product.getId() == null) {
                continue;
            }
            if (excluded != null && excluded.contains(product.getId())) {
                continue;
            }
            double rankScore = (double) (total - i) / Math.max(total, 1);
            double contentScore = profile == null ? 0D : calculateContentScore(product, profile);
            double finalScore = contentScore > 0D
                    ? contentScore * 0.84 + rankScore * 0.16
                    : rankScore * 0.32;
            if (finalScore <= 0D) {
                continue;
            }
            scores.put(product.getId(), finalScore);
            if (scores.size() >= maxKeep) {
                break;
            }
        }
        return scores;
    }

    private Map<Long, Double> normalizeScores(Map<Long, Double> rawScores) {
        if (rawScores == null || rawScores.isEmpty()) {
            return Collections.emptyMap();
        }
        double maxScore = rawScores.values().stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0D);
        if (maxScore <= 0D) {
            return Collections.emptyMap();
        }
        Map<Long, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> entry : rawScores.entrySet()) {
            Long productId = entry.getKey();
            Double score = entry.getValue();
            if (productId == null || score == null || score <= 0D) {
                continue;
            }
            normalized.put(productId, score / maxScore);
        }
        return normalized;
    }

    private Map<Long, Double> sortByScoreDesc(Map<Long, Double> rawScores) {
        if (rawScores == null || rawScores.isEmpty()) {
            return Collections.emptyMap();
        }
        return rawScores.entrySet().stream()
                .sorted((left, right) -> {
                    int cmp = Double.compare(right.getValue(), left.getValue());
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Long.compare(left.getKey(), right.getKey());
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    private static class UserProfile {
        Set<String> tags = new HashSet<>();
        Map<String, Double> tagWeights = new HashMap<>();
        Map<Long, Double> categoryWeights = new HashMap<>();
        double avgPrice = 0;
    }
}
