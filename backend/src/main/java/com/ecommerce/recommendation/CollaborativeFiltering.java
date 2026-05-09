package com.ecommerce.recommendation;

import com.ecommerce.entity.Product;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.utils.RedisUtil;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 协同过滤推荐算法
 *
 * User-Based CF:
 *   1) 构建用户-商品加权评分矩阵 R[u][i] (purchase=8, favorite=3, cart=2, search=2, view=1)
 *   2) 计算用户间余弦相似度 sim(u,v) = R[u]·R[v] / (||R[u]|| × ||R[v]||)
 *   3) 选取Top-K相似用户，推荐其交互但目标用户未交互的商品
 *   4) 根据相似度加权打分排序
 *
 * Item-Based CF:
 *   1) 基于商品共现矩阵 C[i][j] = |Users(i) ∩ Users(j)|
 *   2) 归一化为 Jaccard系数: J(i,j) = C[i][j] / |Users(i) ∪ Users(j)|
 *   3) 返回共现度/Jaccard系数最高的Top-N商品
 *
 * 缓存策略:
 *   - 用户评分向量缓存到 Redis（TTL 30分钟），避免每次请求走 DB
 *   - 用户间相似度优先读取 OfflineDataProcessor 的离线计算结果
 *   - 缓存未命中时回退到实时计算
 */
@Component
public class CollaborativeFiltering {

    private static final Logger log = LoggerFactory.getLogger(CollaborativeFiltering.class);

    private static final String CACHE_KEY_USER_VECTOR = "cf:user_vector:v2:";
    private static final int USER_VECTOR_CACHE_MINUTES = 30;

    @Autowired
    private UserBehaviorMapper behaviorMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ProductMapper productMapper;

    /**
     * User-Based CF: 使用余弦相似度找相似用户，加权推荐
     * 返回有序的商品ID列表（仅兼容旧接口）
     */
    public List<Long> userBasedRecommend(Long userId, int topN) {
        return userBasedRecommendWithScores(userId, topN).entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * User-Based CF: 返回 productId -> score 映射，供混合引擎直接使用原始分数
     *
     * 优化点：
     * 1. 相似度计算阶段缓存候选用户向量，评分阶段直接复用，避免重复 DB/Redis 查询
     * 2. 使用带时间衰减的评分向量，近期行为权重更高
     */
    public Map<Long, Double> userBasedRecommendWithScores(Long userId, int topN) {
        int safeTopN = Math.max(1, topN);
        Map<Long, Double> userVector = buildUserVector(userId);
        if (userVector.isEmpty()) {
            return buildBehaviorFallbackScores(userId, safeTopN, Collections.emptyList(), "no_user_vector");
        }
        Set<Long> userInteracted = userVector.keySet();

        List<Long> candidateUsers = behaviorMapper.selectSimilarUsers(userId, 50);
        if (candidateUsers.isEmpty()) {
            return buildBehaviorFallbackScores(userId, safeTopN, Collections.emptyList(), "no_similar_users");
        }

        Map<Long, Double> userSimilarities = new LinkedHashMap<>();
        Map<Long, Map<Long, Double>> candidateVectorCache = new HashMap<>();
        int cacheHits = 0;

        for (Long candidateId : candidateUsers) {
            double similarity = getCachedSimilarity(userId, candidateId);
            if (similarity >= 0) {
                cacheHits++;
            } else {
                Map<Long, Double> candidateVector = buildUserVector(candidateId);
                if (candidateVector.isEmpty()) continue;
                candidateVectorCache.put(candidateId, candidateVector);
                similarity = cosineSimilarity(userVector, candidateVector);
            }
            if (similarity > 0.01) {
                userSimilarities.put(candidateId, similarity);
            }
        }

        log.debug("[User-CF] 用户{} 相似度计算: 缓存命中{}/{}, 本地向量缓存{}个",
                userId, cacheHits, candidateUsers.size(), candidateVectorCache.size());

        List<Map.Entry<Long, Double>> sortedSimilar = userSimilarities.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toList());

        if (sortedSimilar.isEmpty()) {
            return buildBehaviorFallbackScores(userId, safeTopN, candidateUsers, "no_positive_similarity");
        }

        Map<Long, Double> productScores = new HashMap<>();
        for (Map.Entry<Long, Double> entry : sortedSimilar) {
            Long simUserId = entry.getKey();
            double similarity = entry.getValue();

            Map<Long, Double> simUserVector = candidateVectorCache.get(simUserId);
            if (simUserVector == null) {
                simUserVector = buildUserVector(simUserId);
            }
            for (Map.Entry<Long, Double> productEntry : simUserVector.entrySet()) {
                Long productId = productEntry.getKey();
                if (!userInteracted.contains(productId)) {
                    double weightedScore = similarity * productEntry.getValue();
                    productScores.merge(productId, weightedScore, Double::sum);
                }
            }
        }

        if (productScores.isEmpty()) {
            return buildBehaviorFallbackScores(userId, safeTopN, candidateUsers, "no_cf_candidates");
        }

        log.debug("[User-CF] 用户{} 推荐{}个商品，相似用户{}个", userId, productScores.size(), sortedSimilar.size());
        return sortByScoreDesc(normalizeScores(productScores));
    }

    /**
     * Item-Based CF: 基于商品共现矩阵推荐相似商品
     */
    public List<Long> itemBasedRecommend(Long productId, int topN) {
        return behaviorMapper.selectSimilarProductIds(productId, topN);
    }

    /**
     * 余弦相似度计算
     * cosine_similarity(A, B) = (A · B) / (||A|| × ||B||)
     *
     * 使用 Apache Commons Math 的 RealVector 实现精确计算
     */
    public double cosineSimilarity(Map<Long, Double> vectorA, Map<Long, Double> vectorB) {
        Set<Long> allKeys = new HashSet<>();
        allKeys.addAll(vectorA.keySet());
        allKeys.addAll(vectorB.keySet());

        if (allKeys.isEmpty()) return 0.0;

        double[] a = new double[allKeys.size()];
        double[] b = new double[allKeys.size()];
        int i = 0;
        for (Long key : allKeys) {
            a[i] = vectorA.getOrDefault(key, 0.0);
            b[i] = vectorB.getOrDefault(key, 0.0);
            i++;
        }

        RealVector va = new ArrayRealVector(a);
        RealVector vb = new ArrayRealVector(b);

        double normProduct = va.getNorm() * vb.getNorm();
        if (normProduct == 0) return 0.0;

        return va.dotProduct(vb) / normProduct;
    }

    /**
     * 构建用户加权评分向量（带时间衰减），优先从 Redis 缓存读取
     * 权重: purchase=8, favorite=3, cart=2, search=2, view=1
     * 时间衰减: score × e^(-0.01 × daysSinceEvent)，半衰期约 69 天
     */
    public Map<Long, Double> buildUserVector(Long userId) {
        String cacheKey = CACHE_KEY_USER_VECTOR + userId;
        try {
            Object cached = redisUtil.get(cacheKey);
            if (cached != null) {
                Map<Long, Double> cachedVector = parseVectorFromCache(cached);
                if (!cachedVector.isEmpty()) {
                    return cachedVector;
                }
                redisUtil.delete(cacheKey);
                log.warn("[User-CF] 用户{} 向量缓存解析失败，已删除损坏缓存并回退数据库重建", userId);
            }
        } catch (Exception e) {
            log.debug("[User-CF] Redis缓存读取失败: {}", e.getMessage());
        }

        List<Map<String, Object>> scores;
        try {
            scores = behaviorMapper.selectUserProductScoresWithDecay(userId);
        } catch (Exception e) {
            log.debug("[User-CF] 时间衰减查询失败，回退到基础查询: {}", e.getMessage());
            scores = behaviorMapper.selectUserProductScores(userId);
        }

        Map<Long, Double> vector = new HashMap<>();
        for (Map<String, Object> row : scores) {
            Long productId = Long.valueOf(row.get("product_id").toString());
            double weightedScore = Double.parseDouble(row.get("weighted_score").toString());
            vector.put(productId, weightedScore);
        }

        if (!vector.isEmpty()) {
            try {
                redisUtil.set(cacheKey, vector, USER_VECTOR_CACHE_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.debug("[User-CF] Redis缓存写入失败: {}", e.getMessage());
            }
        }

        return vector;
    }

    public void invalidateUserVectorCache(Long userId) {
        if (userId == null) {
            return;
        }
        String cacheKey = CACHE_KEY_USER_VECTOR + userId;
        try {
            redisUtil.delete(cacheKey);
        } catch (Exception e) {
            log.debug("[User-CF] Redis缓存删除失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 获取用户对之间的相似度（优先从离线缓存读取）
     */
    public double getUserSimilarity(Long userId1, Long userId2) {
        double cached = getCachedSimilarity(userId1, userId2);
        if (cached >= 0) {
            return cached;
        }
        Map<Long, Double> v1 = buildUserVector(userId1);
        Map<Long, Double> v2 = buildUserVector(userId2);
        return cosineSimilarity(v1, v2);
    }

    /**
     * 从 Redis 读取离线计算的相似度
     * @return 相似度值，缓存未命中返回 -1
     */
    private double getCachedSimilarity(Long userId1, Long userId2) {
        Long u1 = Math.min(userId1, userId2);
        Long u2 = Math.max(userId1, userId2);
        String cacheKey = OfflineDataProcessor.CACHE_KEY_USER_SIM + u1 + ":" + u2;
        try {
            Object cached = redisUtil.get(cacheKey);
            if (cached != null) {
                return Double.parseDouble(cached.toString());
            }
        } catch (Exception e) {
            log.debug("[User-CF] 离线相似度缓存读取失败: {}", e.getMessage());
        }
        return -1;
    }

    private Map<Long, Double> buildBehaviorFallbackScores(Long userId,
                                                          int topN,
                                                          List<Long> seedUsers,
                                                          String reason) {
        int safeTopN = Math.max(1, topN);
        List<Long> candidateUsers = new ArrayList<>();
        appendDistinctUsers(candidateUsers, seedUsers, userId, Math.max(safeTopN * 6, 60));
        if (candidateUsers.size() < Math.max(6, safeTopN * 2)) {
            appendDistinctUsers(candidateUsers,
                    behaviorMapper.selectActiveUserIds(Math.max(safeTopN * 12, 80)),
                    userId,
                    Math.max(safeTopN * 8, 96));
        }

        Map<Long, Double> fallbackScores = new LinkedHashMap<>();
        if (!candidateUsers.isEmpty()) {
            try {
                List<Long> behaviorFallbackIds = behaviorMapper.selectRecommendedProductIds(
                        candidateUsers,
                        userId,
                        Math.max(safeTopN * 4, 24));
                fallbackScores.putAll(rankToScores(behaviorFallbackIds));
            } catch (Exception exception) {
                log.debug("[User-CF] 行为兜底查询失败 userId={}: {}", userId, exception.getMessage());
            }
        }

        if (fallbackScores.isEmpty()) {
            fallbackScores.putAll(buildDiversifiedHotFallbackScores(safeTopN));
        }

        if (fallbackScores.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Double> normalized = sortByScoreDesc(normalizeScores(fallbackScores));
        log.info("[User-CF] 用户{} 触发召回降级链路 reason={}, output={}",
                userId, reason, normalized.size());
        return normalized;
    }

    private Map<Long, Double> buildDiversifiedHotFallbackScores(int topN) {
        int safeTopN = Math.max(1, topN);
        List<Product> hotProducts = productMapper.selectHotProducts(Math.max(safeTopN * 8, 64));
        if (hotProducts == null || hotProducts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Deque<Product>> byCategory = new LinkedHashMap<>();
        for (Product product : hotProducts) {
            if (product == null || product.getId() == null) {
                continue;
            }
            String categoryKey = resolveCategoryKey(product);
            byCategory.computeIfAbsent(categoryKey, ignored -> new ArrayDeque<>()).add(product);
        }
        if (byCategory.isEmpty()) {
            return Collections.emptyMap();
        }

        int targetSize = Math.max(safeTopN * 4, 24);
        List<Long> rankedIds = new ArrayList<>(targetSize);
        Set<Long> seen = new LinkedHashSet<>();
        while (rankedIds.size() < targetSize) {
            boolean hasMore = false;
            for (Deque<Product> queue : byCategory.values()) {
                if (queue == null || queue.isEmpty()) {
                    continue;
                }
                hasMore = true;
                Product product = queue.pollFirst();
                if (product != null && product.getId() != null && seen.add(product.getId())) {
                    rankedIds.add(product.getId());
                    if (rankedIds.size() >= targetSize) {
                        break;
                    }
                }
            }
            if (!hasMore) {
                break;
            }
        }
        if (rankedIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return rankToScores(rankedIds);
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

    private void appendDistinctUsers(List<Long> bucket,
                                     List<Long> source,
                                     Long userId,
                                     int maxSize) {
        if (source == null || source.isEmpty() || bucket.size() >= maxSize) {
            return;
        }
        Set<Long> seen = new LinkedHashSet<>(bucket);
        for (Long candidateId : source) {
            if (candidateId == null || candidateId <= 0 || Objects.equals(candidateId, userId) || seen.contains(candidateId)) {
                continue;
            }
            bucket.add(candidateId);
            seen.add(candidateId);
            if (bucket.size() >= maxSize) {
                return;
            }
        }
    }

    private Map<Long, Double> rankToScores(List<Long> rankedIds) {
        if (rankedIds == null || rankedIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Double> scores = new LinkedHashMap<>();
        int total = rankedIds.size();
        for (int i = 0; i < rankedIds.size(); i++) {
            Long productId = rankedIds.get(i);
            if (productId == null || productId <= 0) {
                continue;
            }
            double score = (double) (total - i) / Math.max(total, 1);
            scores.putIfAbsent(productId, score);
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

    @SuppressWarnings("unchecked")
    private Map<Long, Double> parseVectorFromCache(Object cached) {
        if (cached == null) {
            return Collections.emptyMap();
        }

        if (cached instanceof Map) {
            return convertRawVectorMap((Map<?, ?>) cached);
        }

        try {
            String serialized = cached.toString();
            if (!StringUtils.hasText(serialized)) {
                return Collections.emptyMap();
            }

            String normalized = serialized.trim();
            if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                    || (normalized.startsWith("'") && normalized.endsWith("'"))) {
                normalized = normalized.substring(1, normalized.length() - 1)
                        .replace("\\\"", "\"")
                        .replace("\\'", "'");
            }

            try {
                Map<String, Object> raw = com.alibaba.fastjson2.JSON.parseObject(normalized, Map.class);
                if (raw != null && !raw.isEmpty()) {
                    return convertRawVectorMap(raw);
                }
            } catch (Exception ignored) {
            }

            if (!normalized.startsWith("{") || !normalized.endsWith("}")) {
                return Collections.emptyMap();
            }

            String body = normalized.substring(1, normalized.length() - 1).trim();
            if (!StringUtils.hasText(body)) {
                return Collections.emptyMap();
            }

            Map<Long, Double> vector = new LinkedHashMap<>();
            for (String part : body.split(",")) {
                if (!StringUtils.hasText(part)) {
                    continue;
                }
                String[] pair = part.split("[:=]", 2);
                if (pair.length != 2) {
                    continue;
                }
                String keyText = pair[0].trim().replace("\"", "").replace("'", "");
                String valueText = pair[1].trim().replace("\"", "").replace("'", "");
                if (!StringUtils.hasText(keyText) || !StringUtils.hasText(valueText)) {
                    continue;
                }
                vector.put(Long.valueOf(keyText), Double.parseDouble(valueText));
            }
            return vector;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<Long, Double> convertRawVectorMap(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Double> vector = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            vector.put(Long.valueOf(entry.getKey().toString()),
                    Double.parseDouble(entry.getValue().toString()));
        }
        return vector;
    }
}
