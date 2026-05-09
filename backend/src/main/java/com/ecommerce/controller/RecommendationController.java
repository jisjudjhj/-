package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Result;
import com.ecommerce.dto.RecommendationEventDTO;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserPreference;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.recommendation.UserPreferenceBootstrapService;
import com.ecommerce.recommendation.RecommendationGovernanceService;
import com.ecommerce.recommendation.RecommendationRealtimeCacheService;
import com.ecommerce.recommendation.CollaborativeFiltering;
import com.ecommerce.recommendation.HybridRecommendationEngine;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.RecommendationAsyncService;
import com.ecommerce.service.RecommendationService;
import com.ecommerce.service.SeckillService;
import com.ecommerce.service.StreamRealtimeRedisSinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendation", description = "个性化推荐、相似商品与行为上报接口")
public class RecommendationController {

    private static final Set<String> VALID_BEHAVIORS = new HashSet<>(Arrays.asList(
            Constants.BehaviorType.VIEW,
            Constants.BehaviorType.CART,
            Constants.BehaviorType.FAVORITE,
            Constants.BehaviorType.DISLIKE,
            Constants.BehaviorType.PURCHASE,
            Constants.BehaviorType.SEARCH
    ));
    private static final Set<String> VALID_RECOMMENDATION_EVENTS = new HashSet<>(Arrays.asList(
            Constants.RecommendationEventType.EXPOSURE,
            Constants.RecommendationEventType.CLICK,
            Constants.RecommendationEventType.DWELL,
            Constants.RecommendationEventType.ADD_CART,
            Constants.RecommendationEventType.ORDER,
            Constants.RecommendationEventType.REFUND
    ));
    private static final List<String> HENAN_HOT_KEYWORDS = Arrays.asList(
            "河南特色", "河南", "信阳", "毛尖", "南湾湖", "道口", "烧鸡", "洛阳", "牡丹",
            "唐三彩", "汝瓷", "钧瓷", "禹州", "南阳", "玉雕", "玉佩", "玉叶", "玉龙",
            "胡辣汤", "逍遥镇", "方中山", "开封", "桶子鸡", "烩面", "小磨香油",
            "怀山药", "铁棍山药", "焦作", "少林", "新郑", "红薯粉条"
    );
    private static final List<String> HENAN_LOCALITY_KEYWORDS = Arrays.asList(
            "信阳", "南湾湖", "道口", "洛阳", "唐三彩", "汝瓷", "钧瓷", "禹州",
            "南阳", "胡辣汤", "逍遥镇", "方中山", "开封", "烩面", "焦作", "少林", "新郑"
    );
    private static final List<String> HENAN_STRONG_HOT_KEYWORDS = Arrays.asList(
            "信阳", "毛尖", "南湾湖", "道口", "烧鸡", "洛阳", "牡丹", "唐三彩",
            "汝瓷", "钧瓷", "禹州", "南阳", "玉雕", "玉佩", "胡辣汤",
            "逍遥镇", "方中山", "开封", "桶子鸡", "烩面", "怀山药",
            "铁棍山药", "少林", "新郑", "红薯粉条"
    );

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private HybridRecommendationEngine hybridRecommendationEngine;

    @Autowired
    private CollaborativeFiltering collaborativeFiltering;

    @Autowired
    private RecommendationGovernanceService recommendationGovernanceService;

    @Autowired
    private RecommendationRealtimeCacheService recommendationRealtimeCacheService;

    @Autowired
    private RecommendationAsyncService recommendationAsyncService;

    @Autowired(required = false)
    private StreamRealtimeRedisSinkService streamRealtimeRedisSinkService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserBehaviorMapper userBehaviorMapper;

    @Autowired
    private com.ecommerce.mapper.UserPreferenceMapper userPreferenceMapper;

    @Autowired
    private UserPreferenceBootstrapService userPreferenceBootstrapService;

    @Autowired
    private SeckillService seckillService;

    @PostMapping("/interest")
    @Operation(summary = "保存兴趣偏好", description = "新用户选择感兴趣的分类,写入偏好表用于冷启动推荐。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> saveInterest(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("recommendation");
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        List<String> categoryIds = normalizeCategoryIds(body.get("categoryIds"));
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new BusinessException("请至少选择一个分类");
        }
        Map<String, Integer> catPrefs = new java.util.LinkedHashMap<>();
        for (String cid : categoryIds) {
            catPrefs.put(cid, 1);
        }
        com.ecommerce.entity.UserPreference pref = userPreferenceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ecommerce.entity.UserPreference>()
                        .eq(com.ecommerce.entity.UserPreference::getUserId, userId));
        if (pref == null) {
            pref = new com.ecommerce.entity.UserPreference();
            pref.setUserId(userId);
            pref.setCategoryPreferences(catPrefs);
            try {
                userPreferenceMapper.insert(pref);
            } catch (DuplicateKeyException ex) {
                pref = userPreferenceMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ecommerce.entity.UserPreference>()
                                .eq(com.ecommerce.entity.UserPreference::getUserId, userId)
                                .last("LIMIT 1"));
                if (pref == null) {
                    throw ex;
                }
                pref.setCategoryPreferences(catPrefs);
                userPreferenceMapper.updateById(pref);
            }
        } else {
            pref.setCategoryPreferences(catPrefs);
            userPreferenceMapper.updateById(pref);
        }
        return Result.success("偏好保存成功");
    }

    private List<String> normalizeCategoryIds(Object rawValue) {
        if (!(rawValue instanceof Collection<?>)) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Object item : (Collection<?>) rawValue) {
            String value = item == null ? "" : String.valueOf(item).trim();
            if (value.isEmpty()) {
                continue;
            }
            ids.add(value);
        }
        return new ArrayList<>(ids);
    }

    @GetMapping("/interest/status")
    @Operation(summary = "获取兴趣弹窗状态", description = "判断用户是否首次进入，需要展示兴趣选择弹窗。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> interestStatus(HttpServletRequest request) {
        moduleSwitchService.requireEnabled("recommendation");
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        UserPreference pref = userPreferenceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, userId));

        boolean showInterestPopup = false;
        boolean hasSelectedInterest = false;

        if (pref == null || pref.getCategoryPreferences() == null || pref.getCategoryPreferences().isEmpty()) {
            pref = userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        }
        Map<String, Integer> categoryPrefs = pref == null ? null : pref.getCategoryPreferences();
        hasSelectedInterest = categoryPrefs != null && !categoryPrefs.isEmpty();
        showInterestPopup = !hasSelectedInterest;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("showInterestPopup", showInterestPopup);
        data.put("hasSelectedInterest", hasSelectedInterest);
        return Result.success(data);
    }

    @GetMapping("/personal")
    @Operation(summary = "获取个性化推荐", description = "基于当前登录用户的行为、分群与混合推荐策略返回推荐商品。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> personal(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("recommendation");
        Long userId = (Long) request.getAttribute("userId");
        userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        int safeLimit = Math.min(limit, 50);
        List<Product> products = shouldUseEnterpriseRecommendation(userId, SCENE_PERSONAL)
                ? recommendationService.getPersonalRecommendations(userId, safeLimit)
                : recommendationService.getHotRecommendations(userId, safeLimit);
        products = sanitizeRecommendationProducts(products, safeLimit);
        recommendationGovernanceService.stampProducts(products, SCENE_PERSONAL);
        return Result.success(products);
    }

    @GetMapping("/similar/{productId}")
    @Operation(summary = "获取相似商品", description = "根据商品 ID 返回相似商品推荐列表。")
    public Result<?> similar(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "6") int limit,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("recommendation");
        Long userId = (Long) request.getAttribute("userId");
        List<Product> products = recommendationService.getSimilarProducts(userId, productId, Math.min(limit, 50));
        products = sanitizeRecommendationProducts(products, Math.min(limit, 50));
        recommendationGovernanceService.stampProducts(products, SCENE_SIMILAR);
        return Result.success(products);
    }

    @GetMapping("/hot")
    @Operation(summary = "获取热门推荐", description = "返回平台热门商品列表，可用于未登录和冷启动场景。")
    public Result<?> hot(@RequestParam(defaultValue = "10") int limit,
                         HttpServletRequest request) {
        moduleSwitchService.requireEnabled("recommendation");
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null && userId > 0) {
            userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        }
        List<Product> products = recommendationService.getHotRecommendations(userId, Math.min(limit, 50));
        products = sanitizeRecommendationProducts(products, Math.min(limit, 50));
        recommendationGovernanceService.stampProducts(products, SCENE_HOT);
        return Result.success(products);
    }

    @GetMapping("/algorithm")
    @Operation(summary = "按算法获取首页推荐", description = "用于小程序首页对比混合算法、协同过滤和热点推荐的差异。")
    public Result<?> algorithm(
            @RequestParam(defaultValue = "hybrid") String algorithm,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String visitorId,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("recommendation");
        Long userId = (Long) request.getAttribute("userId");
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String normalizedAlgorithm = normalizeHomeAlgorithm(algorithm);
        String visitorSeed = resolveVisitorSeed(visitorId, request);

        if (userId != null && userId > 0) {
            userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        }

        List<Product> products;
        String scene;
        if ("cf".equals(normalizedAlgorithm)) {
            scene = "collaborative_filtering";
            products = loadCollaborativeProducts(userId, safeLimit, visitorSeed);
            products = recommendationService.rerankFeedRecommendations(
                    userId, products, safeLimit, scene, "home_collaborative_filtering");
        } else if ("hot".equals(normalizedAlgorithm)) {
            scene = SCENE_HOT;
            if (userId != null && userId > 0) {
                products = recommendationService.getHotRecommendations(userId, safeLimit);
                attachAlgorithmReason(products, "热点推荐", "河南特色、全站热度、销量和口碑综合排序");
            } else {
                products = loadFeaturedHotProducts(safeLimit);
                attachHotScores(products);
                attachAlgorithmReason(products, "热点推荐", "河南特色、全站热度、销量和口碑综合排序");
            }
        } else {
            scene = SCENE_GUESS_YOU_LIKE;
            if (userId != null && userId > 0) {
                products = recommendationService.guessYouLike(userId, safeLimit, true);
                attachAlgorithmReason(products, "混合算法", "融合协同过滤、内容偏好与商品热度综合排序");
            } else {
                products = loadHybridProducts(userId, safeLimit, visitorSeed);
            }
        }

        products = sanitizeRecommendationProducts(products, safeLimit);
        recommendationGovernanceService.stampProducts(products, scene);
        return Result.success(products);
    }

    @GetMapping("/realtime-hot")
    @Operation(summary = "获取实时热榜", description = "返回实时流计算热榜，可按 1m/1h/1d 窗口查看。")
    public Result<?> realtimeHot(@RequestParam(defaultValue = "1h") String window,
                                 @RequestParam(defaultValue = "10") int limit) {
        return Result.success(loadRealtimeHotProducts(window, limit));
    }

    @GetMapping("/realtime-hot/overview")
    @Operation(summary = "获取实时热榜多窗口概览", description = "一次性返回 1m / 1h / 1d 热榜列表。")
    public Result<?> realtimeHotOverview(@RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lastUpdate", null);

        Map<String, List<Map<String, Object>>> windows = new LinkedHashMap<>();
        windows.put("1m", loadRealtimeHotProducts("1m", limit));
        windows.put("1h", loadRealtimeHotProducts("1h", limit));
        windows.put("1d", loadRealtimeHotProducts("1d", limit));

        payload.put("windows", windows);
        payload.put("available", streamRealtimeRedisSinkService != null);
        return Result.success(payload);
    }

    @GetMapping("/guess-you-like")
    @Operation(summary = "猜你喜欢", description = "根据用户兴趣和推荐策略返回猜你喜欢结果。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> guessYouLike(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("recommendation");
        Long userId = (Long) request.getAttribute("userId");
        String role = String.valueOf(request.getAttribute("role"));
        userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        boolean forcePersonalized = Constants.Role.USER.equalsIgnoreCase(role);
        int safeLimit = Math.min(limit, 50);
        List<Product> products = shouldUseEnterpriseRecommendation(userId, SCENE_GUESS_YOU_LIKE)
                ? recommendationService.guessYouLike(userId, safeLimit, forcePersonalized)
                : recommendationService.getHotRecommendations(userId, safeLimit);
        products = sanitizeRecommendationProducts(products, safeLimit);
        recommendationGovernanceService.stampProducts(products, SCENE_GUESS_YOU_LIKE);
        return Result.success(products);
    }

    @GetMapping("/user-insight")
    @Operation(summary = "获取小程序推荐洞察", description = "面向用户端返回推荐画像、分群摘要和推荐策略说明。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> userInsight(@RequestParam(defaultValue = "8") int limit,
                                 HttpServletRequest request) {
        moduleSwitchService.requireEnabled("recommendation");
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null || userId <= 0) {
            throw new BusinessException("请先登录");
        }
        String role = String.valueOf(request.getAttribute("role"));
        boolean forcePersonalized = Constants.Role.USER.equalsIgnoreCase(role);
        Map<String, Object> dashboard = recommendationService.getRealtimeRecommendationDashboard(
                userId,
                Math.min(limit, 20),
                forcePersonalized);
        return Result.success(buildMiniProgramInsight(dashboard));
    }

    private String normalizeHomeAlgorithm(String algorithm) {
        String value = algorithm == null ? "" : algorithm.trim().toLowerCase(Locale.ROOT);
        if ("collaborative".equals(value) || "collaborative_filtering".equals(value) || "cf".equals(value)) {
            return "cf";
        }
        if ("hot".equals(value) || "popular".equals(value) || "popularity".equals(value)) {
            return "hot";
        }
        return "hybrid";
    }

    private List<Product> sanitizeRecommendationProducts(List<Product> products, int limit) {
        List<Product> filtered = seckillService.excludeShelfSeckillProducts(products);
        if (filtered.isEmpty()) {
            return filtered;
        }
        int safeLimit = Math.max(1, Math.min(limit, filtered.size()));
        return new ArrayList<>(filtered.subList(0, safeLimit));
    }

    private List<Product> loadHybridProducts(Long userId, int limit, String visitorSeed) {
        if (userId == null || userId <= 0) {
            List<Product> products = loadGuestHybridProducts(limit, visitorSeed);
            attachAlgorithmReason(products, "混合算法", "游客冷启动：融合热度、评分、河南特色、类目分散和少量探索");
            return products;
        }
        HybridRecommendationEngine.RecommendationDecision decision =
                hybridRecommendationEngine.recommendDetailed(userId, limit * 2);
        List<Product> products = recommendationService.getProductsByIds(decision.getProductIds());
        products = limitProducts(products, limit);
        if (products.size() < limit) {
            products = limitProducts(
                    appendUniqueProducts(products, recommendationService.guessYouLike(userId, limit, true), limit),
                    limit);
        }
        attachRecommendationScores(products, decision.getScoreMap(), decision.getComponentScoreMap(), "hybrid");
        attachAlgorithmReason(products, "混合算法", "融合协同过滤、内容偏好与商品热度综合排序");
        return products;
    }

    private List<Product> loadCollaborativeProducts(Long userId, int limit, String visitorSeed) {
        if (userId == null || userId <= 0) {
            List<Product> products = loadGuestCollaborativeProducts(limit, visitorSeed);
            attachAlgorithmReason(products, "协同过滤", "游客冷启动：基于近期多人浏览、收藏、加购和购买的群体共同行为");
            return products;
        }
        Map<Long, Double> cfScores = collaborativeFiltering.userBasedRecommendWithScores(userId, limit * 3);
        List<Long> ids = new ArrayList<>(cfScores.keySet());
        List<Product> products = recommendationService.getProductsByIds(ids);
        products = limitProducts(products, limit);
        if (products.size() < limit) {
            products = limitProducts(
                    appendUniqueProducts(products, recommendationService.guessYouLike(userId, limit, true), limit),
                    limit);
        }
        attachRecommendationScores(products, cfScores, buildSingleComponentBreakdown(cfScores, "collaborative"), "collaborative");
        attachAlgorithmReason(products, "协同过滤", "来自相似用户的浏览、收藏、加购和购买共同行为");
        return products;
    }

    private List<Product> limitProducts(List<Product> products, int limit) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, products.size()));
        return new ArrayList<>(products.subList(0, safeLimit));
    }

    private List<Product> appendUniqueProducts(List<Product> primary, List<Product> fallback, int limit) {
        LinkedHashMap<Long, Product> merged = new LinkedHashMap<>();
        if (primary != null) {
            for (Product product : primary) {
                if (product != null && product.getId() != null) {
                    merged.putIfAbsent(product.getId(), product);
                }
            }
        }
        if (fallback != null) {
            for (Product product : fallback) {
                if (product != null && product.getId() != null) {
                    merged.putIfAbsent(product.getId(), product);
                    if (merged.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private Map<Long, Map<String, Double>> buildSingleComponentBreakdown(Map<Long, Double> scoreMap, String componentKey) {
        if (scoreMap == null || scoreMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<String, Double>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> entry : scoreMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            Map<String, Double> breakdown = new LinkedHashMap<>();
            breakdown.put(componentKey, entry.getValue());
            result.put(entry.getKey(), breakdown);
        }
        return result;
    }

    private void attachRecommendationScores(List<Product> products,
                                            Map<Long, Double> scoreMap,
                                            Map<Long, Map<String, Double>> componentScoreMap,
                                            String fallbackComponent) {
        if (products == null || products.isEmpty()) {
            return;
        }
        int size = products.size();
        for (int index = 0; index < size; index++) {
            Product product = products.get(index);
            if (product == null || product.getId() == null) {
                continue;
            }
            double fallbackScore = (double) (size - index) / Math.max(size, 1);
            double finalScore = scoreMap == null
                    ? fallbackScore
                    : scoreMap.getOrDefault(product.getId(), fallbackScore);
            Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
            Map<String, Double> rawBreakdown = componentScoreMap == null
                    ? Collections.emptyMap()
                    : componentScoreMap.getOrDefault(product.getId(), Collections.emptyMap());
            for (Map.Entry<String, Double> entry : rawBreakdown.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    breakdown.put(entry.getKey(), toScoreDecimal(entry.getValue()));
                }
            }
            if (!breakdown.containsKey(fallbackComponent)) {
                breakdown.put(fallbackComponent, toScoreDecimal(finalScore));
            }
            breakdown.put("final", toScoreDecimal(finalScore));
            product.setRecommendationScore(toScoreDecimal(finalScore));
            product.setRecommendationScoreBreakdown(breakdown);
        }
    }

    private void attachHotScores(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        int maxSales = products.stream()
                .map(Product::getSalesCount)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        int size = products.size();
        for (int index = 0; index < size; index++) {
            Product product = products.get(index);
            if (product == null) {
                continue;
            }
            double rankScore = (double) (size - index) / Math.max(size, 1);
            double salesScore = maxSales > 0 && product.getSalesCount() != null
                    ? (double) product.getSalesCount() / maxSales
                    : 0D;
            double ratingScore = product.getRating() == null ? 0D : product.getRating().doubleValue() / 5.0D;
            double henanScore = hasHenanSpecialtySignal(product) ? 1D : 0D;
            double localityScore = getHenanLocalityScore(product);
            double finalScore = salesScore * 0.5D
                    + ratingScore * 0.16D
                    + rankScore * 0.1D
                    + henanScore * 0.14D
                    + localityScore * 0.1D;
            Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
            breakdown.put("sales", toScoreDecimal(salesScore));
            breakdown.put("rating", toScoreDecimal(ratingScore));
            breakdown.put("rank", toScoreDecimal(rankScore));
            breakdown.put("henan", toScoreDecimal(henanScore));
            breakdown.put("locality", toScoreDecimal(localityScore));
            breakdown.put("final", toScoreDecimal(finalScore));
            product.setRecommendationScore(toScoreDecimal(finalScore));
            product.setRecommendationScoreBreakdown(breakdown);
        }
        products.sort((left, right) -> right.getRecommendationScore().compareTo(left.getRecommendationScore()));
    }

    private List<Product> loadFeaturedHotProducts(int limit) {
        int safeLimit = Math.max(1, limit);
        List<Product> pool = productMapper.selectHotProducts(Math.max(safeLimit * 12, 80));
        List<Product> featured = new ArrayList<>();
        List<Product> fallback = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        for (Product product : pool) {
            if (product == null || product.getId() == null) {
                continue;
            }
            if (hasStrongHenanHotSignal(product)) {
                if (selectedIds.add(product.getId())) {
                    featured.add(product);
                }
            } else {
                fallback.add(product);
            }
        }
        for (Product product : fallback) {
            if (featured.size() >= safeLimit) {
                break;
            }
            if (product != null && product.getId() != null && selectedIds.add(product.getId())) {
                featured.add(product);
            }
        }
        return limitProducts(featured, safeLimit);
    }

    private boolean hasStrongHenanHotSignal(Product product) {
        if (product == null) {
            return false;
        }
        if (containsAnyStrongHenanSignal(product.getName()) || containsAnyStrongHenanSignal(product.getDescription())) {
            return true;
        }
        List<String> tags = product.getTags();
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        for (String tag : tags) {
            if (containsAnyStrongHenanSignal(tag)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHenanSpecialtySignal(Product product) {
        if (product == null) {
            return false;
        }
        if (containsAnyHenanSignal(product.getName()) || containsAnyHenanSignal(product.getDescription())) {
            return true;
        }
        List<String> tags = product.getTags();
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        for (String tag : tags) {
            if (containsAnyHenanSignal(tag)) {
                return true;
            }
        }
        return false;
    }

    private double getHenanLocalityScore(Product product) {
        if (product == null) {
            return 0D;
        }
        StringBuilder text = new StringBuilder();
        if (StringUtils.hasText(product.getName())) {
            text.append(product.getName()).append(' ');
        }
        if (StringUtils.hasText(product.getDescription())) {
            text.append(product.getDescription()).append(' ');
        }
        List<String> tags = product.getTags();
        if (tags != null) {
            for (String tag : tags) {
                if (StringUtils.hasText(tag)) {
                    text.append(tag).append(' ');
                }
            }
        }
        String value = text.toString();
        int hits = 0;
        for (String keyword : HENAN_LOCALITY_KEYWORDS) {
            if (value.contains(keyword)) {
                hits++;
            }
        }
        if (hits <= 0) {
            return hasHenanSpecialtySignal(product) ? 0.6D : 0D;
        }
        return Math.min(1D, 0.6D + hits * 0.1D);
    }

    private boolean containsAnyHenanSignal(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String keyword : HENAN_HOT_KEYWORDS) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyStrongHenanSignal(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String keyword : HENAN_STRONG_HOT_KEYWORDS) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal toScoreDecimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private List<Product> loadGuestHybridProducts(int limit, String visitorSeed) {
        List<Product> candidates = loadActiveProducts(Math.max(limit * 8, 120), "hybrid");
        if (candidates.isEmpty()) {
            return recommendationService.getHotRecommendations(null, limit);
        }
        Set<Long> hotIds = new LinkedHashSet<>(recommendationService.getHotRecommendations(null, Math.max(limit, 12)).stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList()));
        String seed = StringUtils.hasText(visitorSeed) ? visitorSeed : "guest-hybrid";
        candidates.sort((left, right) -> Double.compare(
                guestHybridScore(right, seed),
                guestHybridScore(left, seed)));
        return diversifyGuestProducts(candidates, limit, hotIds, Math.max(1, (int) Math.floor(limit * 0.6D)));
    }

    private List<Product> loadGuestCollaborativeProducts(int limit, String visitorSeed) {
        List<Product> activeProducts = loadActiveProducts(Math.max(limit * 10, 140), "cf");
        if (activeProducts.isEmpty()) {
            return recommendationService.getHotRecommendations(null, limit);
        }

        Map<Long, Product> productMap = new LinkedHashMap<>();
        for (Product product : activeProducts) {
            if (product != null && product.getId() != null) {
                productMap.put(product.getId(), product);
            }
        }

        Map<Long, Double> scoreMap = new HashMap<>();
        Map<Long, Set<Long>> userSetMap = new HashMap<>();
        List<UserBehavior> recentBehaviors = userBehaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .isNotNull(UserBehavior::getProductId)
                        .in(UserBehavior::getBehaviorType, Arrays.asList(
                                Constants.BehaviorType.VIEW,
                                Constants.BehaviorType.FAVORITE,
                                Constants.BehaviorType.CART,
                                Constants.BehaviorType.PURCHASE))
                        .orderByDesc(UserBehavior::getCreateTime)
                        .last("LIMIT 1800"));

        int index = 0;
        for (UserBehavior behavior : recentBehaviors) {
            if (behavior == null || behavior.getProductId() == null || !productMap.containsKey(behavior.getProductId())) {
                index++;
                continue;
            }
            double recency = Math.max(0.35D, 1.0D - (index / 2200.0D));
            double weight = behaviorWeight(behavior.getBehaviorType()) * recency;
            Long productId = behavior.getProductId();
            scoreMap.merge(productId, weight, Double::sum);
            if (behavior.getUserId() != null && behavior.getUserId() > 0) {
                userSetMap.computeIfAbsent(productId, key -> new HashSet<>()).add(behavior.getUserId());
            }
            index++;
        }

        String seed = StringUtils.hasText(visitorSeed) ? visitorSeed : "guest-cf";
        List<Product> behaviorRanked = new ArrayList<>(activeProducts);
        behaviorRanked.sort((left, right) -> Double.compare(
                guestCollaborativeScore(right, seed, scoreMap, userSetMap),
                guestCollaborativeScore(left, seed, scoreMap, userSetMap)));

        Set<Long> hotIds = new LinkedHashSet<>(recommendationService.getHotRecommendations(null, Math.max(limit, 12)).stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList()));
        return diversifyGuestProducts(behaviorRanked, limit, hotIds, Math.max(1, (int) Math.floor(limit * 0.5D)));
    }

    private List<Product> loadActiveProducts(int limit, String mode) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, Constants.ProductStatus.ON_SHELF)
                .eq(Product::getDeleted, 0);
        if ("hybrid".equals(mode)) {
            wrapper.orderByDesc(Product::getRating)
                    .orderByDesc(Product::getSalesCount)
                    .orderByDesc(Product::getId);
        } else {
            wrapper.orderByDesc(Product::getId)
                    .orderByDesc(Product::getSalesCount);
        }
        wrapper.last("LIMIT " + Math.max(20, Math.min(limit, 240)));
        return productMapper.selectList(wrapper);
    }

    private List<Product> diversifyGuestProducts(List<Product> candidates, int limit, Set<Long> hotIds, int hotOverlapLimit) {
        List<Product> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        Map<Long, Integer> categoryCounts = new HashMap<>();
        int hotOverlap = 0;
        for (Product product : candidates) {
            if (product == null || product.getId() == null || used.contains(product.getId())) {
                continue;
            }
            Long categoryId = product.getCategoryId();
            int categoryCount = categoryCounts.getOrDefault(categoryId, 0);
            boolean isHot = hotIds != null && hotIds.contains(product.getId());
            if (categoryCount >= 2 || (isHot && hotOverlap >= hotOverlapLimit)) {
                continue;
            }
            result.add(product);
            used.add(product.getId());
            categoryCounts.put(categoryId, categoryCount + 1);
            if (isHot) {
                hotOverlap++;
            }
            if (result.size() >= limit) {
                return result;
            }
        }

        for (Product product : candidates) {
            if (product == null || product.getId() == null || used.contains(product.getId())) {
                continue;
            }
            result.add(product);
            used.add(product.getId());
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private double guestHybridScore(Product product, String seed) {
        double salesScore = Math.log10(Math.max(1, safeInt(product == null ? null : product.getSalesCount())) + 10.0D);
        double ratingScore = safeRating(product);
        double henanBoost = hasTag(product, "河南特色") ? 1.25D : 0.0D;
        double recencyBoost = product != null && product.getId() != null ? Math.min(1.2D, product.getId() / 180.0D) : 0.0D;
        double explorationBoost = stableUnit(seed + ":explore:" + safeId(product)) > 0.78D ? 1.05D : 0.0D;
        double jitter = stableUnit(seed + ":hybrid:" + safeId(product)) * 0.85D;
        return salesScore * 1.45D + ratingScore * 1.35D + henanBoost + recencyBoost + explorationBoost + jitter;
    }

    private double guestCollaborativeScore(Product product,
                                           String seed,
                                           Map<Long, Double> scoreMap,
                                           Map<Long, Set<Long>> userSetMap) {
        Long productId = safeId(product);
        double crowdScore = scoreMap.getOrDefault(productId, 0.0D);
        double distinctUserBoost = Math.log1p(userSetMap.getOrDefault(productId, Collections.emptySet()).size()) * 2.2D;
        double quality = safeRating(product) * 0.8D + Math.log10(Math.max(1, safeInt(product == null ? null : product.getSalesCount())) + 10.0D) * 0.45D;
        double seedJitter = stableUnit(seed + ":cf:" + productId) * 1.15D;
        double antiHot = stableUnit(seed + ":cf-long-tail:" + productId) > 0.72D ? 0.9D : 0.0D;
        return crowdScore + distinctUserBoost + quality + seedJitter + antiHot;
    }

    private double behaviorWeight(String behaviorType) {
        if (Constants.BehaviorType.PURCHASE.equals(behaviorType)) {
            return 8.0D;
        }
        if (Constants.BehaviorType.CART.equals(behaviorType)) {
            return 5.0D;
        }
        if (Constants.BehaviorType.FAVORITE.equals(behaviorType)) {
            return 4.0D;
        }
        return 1.4D;
    }

    private boolean hasTag(Product product, String tag) {
        if (product == null || product.getTags() == null || !StringUtils.hasText(tag)) {
            return false;
        }
        for (String item : product.getTags()) {
            if (tag.equals(item)) {
                return true;
            }
        }
        return false;
    }

    private Long safeId(Product product) {
        return product == null || product.getId() == null ? 0L : product.getId();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double safeRating(Product product) {
        return product == null || product.getRating() == null ? 4.5D : product.getRating().doubleValue();
    }

    private double stableUnit(String seed) {
        int hash = seed == null ? 0 : seed.hashCode();
        long positive = Integer.toUnsignedLong(hash);
        return (positive % 10000L) / 10000.0D;
    }

    private String resolveVisitorSeed(String visitorId, HttpServletRequest request) {
        if (StringUtils.hasText(visitorId)) {
            return visitorId.trim();
        }
        String headerValue = request == null ? null : request.getHeader("X-Visitor-Id");
        if (StringUtils.hasText(headerValue)) {
            return headerValue.trim();
        }
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        String remoteAddr = request == null ? "" : request.getRemoteAddr();
        return firstNonBlank(userAgent, "guest") + ":" + firstNonBlank(remoteAddr, "0.0.0.0");
    }

    private String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private void attachAlgorithmReason(List<Product> products, String title, String reason) {
        if (products == null) {
            return;
        }
        for (Product product : products) {
            if (product == null) {
                continue;
            }
            if (!StringUtils.hasText(product.getRecommendReason())) {
                product.setRecommendReason(reason);
            }
            if (!StringUtils.hasText(product.getReasonType())) {
                product.setReasonType(title);
            }
            if (!StringUtils.hasText(product.getSourceType())) {
                product.setSourceType(title);
            }
            if (!StringUtils.hasText(product.getModelVersion())) {
                product.setModelVersion(title);
            }
            if (!StringUtils.hasText(product.getDataFreshness())) {
                product.setDataFreshness("近实时");
            }
            if (product.getMatchedReasonTags() == null || product.getMatchedReasonTags().isEmpty()) {
                product.setMatchedReasonTags(Collections.singletonList(title));
            }
        }
    }

    @PostMapping("/behavior")
    @Operation(summary = "上报推荐行为", description = "记录浏览、加购、收藏、购买、搜索等推荐相关行为。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> recordBehavior(@RequestBody UserBehavior behavior, HttpServletRequest request) {
        if (!moduleSwitchService.isEnabled("recommendation")) {
            return Result.success();
        }
        Long userId = (Long) request.getAttribute("userId");
        behavior.setUserId(userId);

        if (behavior.getBehaviorType() == null || !VALID_BEHAVIORS.contains(behavior.getBehaviorType())) {
            throw new BusinessException("无效的行为类型");
        }
        if (!Constants.BehaviorType.SEARCH.equals(behavior.getBehaviorType())
                && behavior.getProductId() == null) {
            throw new BusinessException("商品ID不能为空");
        }

        UserBehavior payload = new UserBehavior();
        payload.setUserId(behavior.getUserId());
        payload.setProductId(behavior.getProductId());
        payload.setBehaviorType(behavior.getBehaviorType());
        payload.setSearchKeyword(behavior.getSearchKeyword());
        payload.setDuration(behavior.getDuration());
        payload.setCreateTime(behavior.getCreateTime());
        payload.setRecommendationToken(behavior.getRecommendationToken());
        payload.setRecommendationScene(behavior.getRecommendationScene());
        payload.setOrderId(behavior.getOrderId());

        recommendationAsyncService.recordBehaviorAsync(payload);
        return Result.success("行为记录已受理");
    }

    @PostMapping("/events")
    @Operation(summary = "上报统一推荐事件", description = "统一上报 exposure/click/dwell/add_cart/order/refund 事件。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> recordRecommendationEvent(@RequestBody RecommendationEventDTO eventDTO,
                                               HttpServletRequest request) {
        if (!moduleSwitchService.isEnabled("recommendation")) {
            return Result.success("推荐模块已关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (eventDTO == null || !StringUtils.hasText(eventDTO.getEventType())) {
            throw new BusinessException("事件类型不能为空");
        }

        String eventType = eventDTO.getEventType().trim().toLowerCase(Locale.ROOT);
        if (!VALID_RECOMMENDATION_EVENTS.contains(eventType)) {
            throw new BusinessException("无效的事件类型");
        }
        eventDTO.setEventType(eventType);

        boolean orderOnlyEvent = (Constants.RecommendationEventType.ORDER.equals(eventType)
                || Constants.RecommendationEventType.REFUND.equals(eventType))
                && eventDTO.getOrderId() != null;
        if ((Constants.RecommendationEventType.CLICK.equals(eventType)
                || Constants.RecommendationEventType.DWELL.equals(eventType)
                || Constants.RecommendationEventType.ADD_CART.equals(eventType)
                || Constants.RecommendationEventType.ORDER.equals(eventType)
                || Constants.RecommendationEventType.REFUND.equals(eventType))
                && eventDTO.getProductId() == null
                && !orderOnlyEvent) {
            throw new BusinessException("商品ID不能为空");
        }
        recommendationAsyncService.recordRecommendationEventAsync(userId, eventDTO);
        return Result.success("事件已受理");
    }

    @PostMapping("/events/batch")
    @Operation(summary = "批量上报推荐事件", description = "批量上报 exposure/click/dwell/add_cart/order/refund 事件，服务端会做幂等保护。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> recordRecommendationEvents(@RequestBody List<RecommendationEventDTO> eventList,
                                                HttpServletRequest request) {
        if (!moduleSwitchService.isEnabled("recommendation")) {
            return Result.success("推荐模块已关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (eventList == null || eventList.isEmpty()) {
            return Result.success(Collections.singletonMap("accepted", 0));
        }

        int accepted = 0;
        for (RecommendationEventDTO eventDTO : eventList) {
            if (eventDTO == null || !StringUtils.hasText(eventDTO.getEventType())) {
                continue;
            }
            String eventType = eventDTO.getEventType().trim().toLowerCase(Locale.ROOT);
            if (!VALID_RECOMMENDATION_EVENTS.contains(eventType)) {
                continue;
            }
            boolean batchOrderOnlyEvent = (Constants.RecommendationEventType.ORDER.equals(eventType)
                    || Constants.RecommendationEventType.REFUND.equals(eventType))
                    && eventDTO.getOrderId() != null;
            if ((Constants.RecommendationEventType.CLICK.equals(eventType)
                    || Constants.RecommendationEventType.DWELL.equals(eventType)
                    || Constants.RecommendationEventType.ADD_CART.equals(eventType)
                    || Constants.RecommendationEventType.ORDER.equals(eventType)
                    || Constants.RecommendationEventType.REFUND.equals(eventType))
                    && eventDTO.getProductId() == null
                    && !batchOrderOnlyEvent) {
                continue;
            }
            eventDTO.setEventType(eventType);
            recommendationAsyncService.recordRecommendationEventAsync(userId, eventDTO);
            accepted++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", accepted);
        result.put("received", eventList.size());
        return Result.success(result);
    }

    @PostMapping("/cache/invalidate")
    @Operation(summary = "失效推荐缓存", description = "按用户或全局范围失效推荐实时缓存。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> invalidateRecommendationCache(@RequestBody(required = false) Map<String, Object> body,
                                                   HttpServletRequest request) {
        Long userId = parseLong(body == null ? null : body.get("userId"));
        String scope = body == null ? "" : String.valueOf(body.getOrDefault("scope", "")).trim();
        if (userId != null && userId > 0) {
            recommendationRealtimeCacheService.invalidateUser(userId);
        } else if ("hot".equalsIgnoreCase(scope)) {
            recommendationRealtimeCacheService.invalidateHot();
        } else {
            recommendationRealtimeCacheService.invalidateAll();
        }
        return Result.success("推荐缓存已失效");
    }

    @PostMapping("/dislike")
    @Operation(summary = "提交不感兴趣反馈", description = "用户点击不感兴趣后写入负反馈，用于抑制后续推荐。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> dislike(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!moduleSwitchService.isEnabled("recommendation")) {
            return Result.success("推荐模块已关闭");
        }

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        Long productId = parseLong(body == null ? null : body.get("productId"));
        if (productId == null || productId <= 0L) {
            throw new BusinessException("商品ID不能为空");
        }

        UserBehavior payload = new UserBehavior();
        payload.setUserId(userId);
        payload.setProductId(productId);
        payload.setBehaviorType(Constants.BehaviorType.DISLIKE);
        payload.setCreateTime(LocalDateTime.now());

        String recommendationToken = body == null ? "" : String.valueOf(body.getOrDefault("recommendationToken", "")).trim();
        if (StringUtils.hasText(recommendationToken)) {
            payload.setRecommendationToken(recommendationToken);
        }
        String scene = body == null ? "" : String.valueOf(body.getOrDefault("scene", "")).trim();
        if (StringUtils.hasText(scene)) {
            payload.setRecommendationScene(scene);
        }
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", "")).trim();
        if (StringUtils.hasText(reason)) {
            payload.setSearchKeyword(reason);
        }

        recommendationAsyncService.recordBehaviorAsync(payload);
        return Result.success("已减少此类推荐");
    }

    @GetMapping("/personal-with-explanation")
    @Operation(summary = "获取带解释的个性化推荐", description = "返回推荐商品及推荐原因、算法说明等解释信息。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> personalWithExplanation(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("recommendation");
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> result = recommendationService.getPersonalRecommendationsWithExplanation(
                userId, Math.min(limit, 50));
        return Result.success(result);
    }

    private Result<?> emptyRecommendationList() {
        return Result.success(Collections.emptyList());
    }

    private Result<?> emptyRecommendationExplanation() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productIds", Collections.emptyList());
        result.put("products", Collections.emptyList());
        result.put("explanations", Collections.emptyList());
        result.put("algorithmWeights", Collections.emptyMap());
        result.put("experimentGroup", "disabled");
        return Result.success(result);
    }

    private static final String SCENE_PERSONAL = "personal";
    private static final String SCENE_GUESS_YOU_LIKE = "guess_you_like";
    private static final String SCENE_HOT = "hot";
    private static final String SCENE_SIMILAR = "similar";

    private boolean shouldUseEnterpriseRecommendation(Long userId, String scene) {
        boolean inGray = recommendationGovernanceService.shouldUseEnterpriseStrategy(userId, scene);
        return inGray || !recommendationGovernanceService.shouldFallbackToHotWhenOutsideGray();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildMiniProgramInsight(Map<String, Object> dashboard) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (dashboard == null || dashboard.isEmpty()) {
            payload.put("title", "你的实时推荐画像");
            payload.put("summary", "推荐会结合近期浏览、加购、收藏和购买行为动态刷新。");
            payload.put("topCategories", Collections.emptyList());
            payload.put("reasonChips", Arrays.asList("实时行为", "兴趣偏好", "热度补充"));
            payload.put("strategyCards", Collections.emptyList());
            payload.put("dataFreshness", "实时");
            return payload;
        }

        Map<String, Object> profile = dashboard.get("profile") instanceof Map
                ? (Map<String, Object>) dashboard.get("profile")
                : Collections.emptyMap();
        Map<String, Object> segment = dashboard.get("segment") instanceof Map
                ? (Map<String, Object>) dashboard.get("segment")
                : Collections.emptyMap();
        Map<String, Object> recommendation = dashboard.get("recommendation") instanceof Map
                ? (Map<String, Object>) dashboard.get("recommendation")
                : Collections.emptyMap();
        Map<String, Object> dataSource = dashboard.get("dataSource") instanceof Map
                ? (Map<String, Object>) dashboard.get("dataSource")
                : Collections.emptyMap();

        List<String> topCategories = toStringList(profile.get("topCategories"), 3);
        List<String> topTags = toStringList(profile.get("topTags"), 3);
        String segmentName = firstText(
                segment.get("segmentName"),
                segment.get("profileLabel"),
                "实时兴趣人群");
        String profileSummary = firstText(
                profile.get("profileSummary"),
                segment.get("personaSummary"),
                "系统会结合你的近期行为、偏好类目和商品表现持续校准推荐。");
        String sourceType = firstText(recommendation.get("sourceType"), recommendation.get("recommendationSource"), "hybrid");
        String freshness = firstText(dataSource.get("recommendationSource"), recommendation.get("dataFreshness"), "实时");

        payload.put("title", "你的实时推荐画像");
        payload.put("summary", profileSummary);
        payload.put("segmentName", segmentName);
        payload.put("journeyStage", profile.get("journeyStage"));
        payload.put("shoppingMomentum", profile.get("shoppingMomentum"));
        payload.put("topCategories", topCategories);
        payload.put("topTags", topTags);
        payload.put("dataFreshness", normalizeFreshnessLabel(freshness));
        payload.put("reasonChips", buildInsightReasonChips(profile, segment, sourceType));
        payload.put("strategyCards", buildInsightStrategyCards(profile, segment, recommendation, topCategories, topTags));
        return payload;
    }

    private List<Map<String, Object>> buildInsightStrategyCards(Map<String, Object> profile,
                                                               Map<String, Object> segment,
                                                               Map<String, Object> recommendation,
                                                               List<String> topCategories,
                                                               List<String> topTags) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(strategyCard("偏好类目", topCategories.isEmpty() ? "待学习" : topCategories.get(0)));
        cards.add(strategyCard("兴趣标签", topTags.isEmpty() ? "实时补充" : topTags.get(0)));
        cards.add(strategyCard("人群策略", firstText(segment.get("segmentName"), "冷启动保护")));
        cards.add(strategyCard("推荐来源", normalizeSourceLabel(firstText(recommendation.get("sourceType"), profile.get("recommendationSource"), "hybrid"))));
        return cards;
    }

    private Map<String, Object> strategyCard(String title, String value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("value", value);
        return item;
    }

    private List<String> buildInsightReasonChips(Map<String, Object> profile,
                                                 Map<String, Object> segment,
                                                 String sourceType) {
        List<String> chips = new ArrayList<>();
        if (Boolean.TRUE.equals(profile.get("realtimeFeatureEnriched"))) {
            chips.add("实时行为已接入");
        }
        if (Boolean.TRUE.equals(segment.get("available"))) {
            chips.add("分群已命中");
        }
        chips.add(normalizeSourceLabel(sourceType));
        chips.add("曝光去重");
        chips.add("负反馈降权");
        return chips.stream().filter(StringUtils::hasText).distinct().limit(4).collect(java.util.stream.Collectors.toList());
    }

    private List<String> toStringList(Object raw, int limit) {
        if (!(raw instanceof Collection)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (Collection<?>) raw) {
            String value = firstText(item, null);
            if (StringUtils.hasText(value) && !result.contains(value)) {
                result.add(value);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private String normalizeSourceLabel(String sourceType) {
        String value = sourceType == null ? "" : sourceType.toLowerCase(Locale.ROOT);
        if (value.contains("snapshot")) return "离线模型";
        if (value.contains("live")) return "实时行为";
        if (value.contains("cf")) return "相似用户";
        if (value.contains("content")) return "内容匹配";
        if (value.contains("hot")) return "热度趋势";
        if (value.contains("segment")) return "人群策略";
        if (value.contains("hybrid")) return "混合推荐";
        return StringUtils.hasText(sourceType) ? sourceType : "混合推荐";
    }

    private String normalizeFreshnessLabel(String freshness) {
        String value = freshness == null ? "" : freshness.toLowerCase(Locale.ROOT);
        if (value.contains("snapshot")) return "离线快照";
        if (value.contains("realtime") || value.contains("live")) return "实时";
        return StringUtils.hasText(freshness) ? freshness : "实时";
    }

    private String firstText(Object... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private List<Map<String, Object>> loadRealtimeHotProducts(String window, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String safeWindow = normalizeWindow(window);

        List<Map<String, Object>> rows = streamRealtimeRedisSinkService == null
                ? Collections.emptyList()
                : streamRealtimeRedisSinkService.getHotProducts(safeWindow, safeLimit);
        if (rows == null || rows.isEmpty()) {
            return loadHotFallback(safeWindow, safeLimit);
        }

        List<Long> productIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long productId = parseLong(row.get("productId"));
            if (productId != null && productId > 0L && !productIds.contains(productId)) {
                productIds.add(productId);
            }
        }

        Map<Long, Product> productMap = new LinkedHashMap<>();
        if (!productIds.isEmpty()) {
            List<Product> products = productMapper.selectByIds(productIds);
            for (Product product : products) {
                if (product != null && product.getId() != null) {
                    productMap.put(product.getId(), product);
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long productId = parseLong(row.get("productId"));
            Product product = productId == null ? null : productMap.get(productId);
            if (product == null
                    || product.getId() == null
                    || !Objects.equals(product.getStatus(), Constants.ProductStatus.ON_SHELF)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", row.get("rank"));
            item.put("productId", product.getId());
            item.put("score", row.get("score"));
            item.put("window", safeWindow);
            item.put("productName", product.getName());
            item.put("productImage", product.getImage());
            item.put("categoryName", product.getCategoryName());
            item.put("price", product.getPrice());
            item.put("salesCount", product.getSalesCount());
            item.put("rating", product.getRating());
            result.add(item);
        }
        if (result.size() < safeLimit) {
            appendRealtimeHotFallback(result, safeWindow, safeLimit);
        }
        return result;
    }

    private void appendRealtimeHotFallback(List<Map<String, Object>> result, String window, int limit) {
        Set<Long> existingIds = new HashSet<>();
        for (Map<String, Object> item : result) {
            Long productId = parseLong(item.get("productId"));
            if (productId != null) {
                existingIds.add(productId);
            }
        }

        List<Map<String, Object>> fallback = loadHotFallback(window, limit);
        for (Map<String, Object> item : fallback) {
            Long productId = parseLong(item.get("productId"));
            if (productId == null || existingIds.contains(productId)) {
                continue;
            }
            item.put("rank", result.size() + 1);
            result.add(item);
            existingIds.add(productId);
            if (result.size() >= limit) {
                break;
            }
        }
    }

    private List<Map<String, Object>> loadHotFallback(String window, int limit) {
        List<Product> products = productMapper.selectHotProducts(limit);
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank++);
            item.put("productId", product.getId());
            item.put("score", estimateFallbackScore(product));
            item.put("window", window);
            item.put("productName", product.getName());
            item.put("productImage", product.getImage());
            item.put("categoryName", product.getCategoryName());
            item.put("price", product.getPrice());
            item.put("salesCount", product.getSalesCount());
            item.put("rating", product.getRating());
            result.add(item);
        }
        return result;
    }

    private BigDecimal estimateFallbackScore(Product product) {
        long salesCount = product.getSalesCount() == null ? 0L : product.getSalesCount();
        BigDecimal rating = product.getRating() == null ? BigDecimal.ZERO : product.getRating();
        return BigDecimal.valueOf(salesCount).multiply(BigDecimal.valueOf(0.7))
                .add(rating.multiply(BigDecimal.valueOf(12)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeWindow(String window) {
        if (!StringUtils.hasText(window)) {
            return "1h";
        }
        String normalized = window.trim().toLowerCase();
        if ("1m".equals(normalized) || "1h".equals(normalized) || "1d".equals(normalized)) {
            return normalized;
        }
        return "1h";
    }

    private Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        try {
            return (long) Double.parseDouble(String.valueOf(raw).trim());
        } catch (Exception ignore) {
            return null;
        }
    }
}
