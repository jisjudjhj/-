package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.Constants;
import com.ecommerce.dto.RecommendationEventDTO;
import com.ecommerce.entity.AnalyticsKmeansSegment;
import com.ecommerce.entity.AnalyticsKmeansTask;
import com.ecommerce.entity.AnalyticsKmeansUserResult;
import com.ecommerce.entity.AnalyticsProductSimilarity;
import com.ecommerce.entity.AnalyticsRecommendationExposure;
import com.ecommerce.entity.AnalyticsRecommendationResult;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.RecommendationEvent;
import com.ecommerce.entity.StreamProductHotnessRealtime;
import com.ecommerce.entity.StreamUserCategoryPreference;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.AnalyticsKmeansSegmentMapper;
import com.ecommerce.mapper.AnalyticsKmeansTaskMapper;
import com.ecommerce.mapper.AnalyticsKmeansUserResultMapper;
import com.ecommerce.mapper.AnalyticsProductSimilarityMapper;
import com.ecommerce.mapper.AnalyticsRecommendationExposureMapper;
import com.ecommerce.mapper.AnalyticsRecommendationResultMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.RecommendationEventMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.recommendation.ABTestFramework;
import com.ecommerce.recommendation.CollaborativeFiltering;
import com.ecommerce.recommendation.HybridRecommendationEngine;
import com.ecommerce.recommendation.RecommendationAttributionService;
import com.ecommerce.recommendation.RecommendationNegativeFeedbackPolicy;
import com.ecommerce.recommendation.RecommendationRecallService;
import com.ecommerce.recommendation.RecommendationRerankService;
import com.ecommerce.recommendation.RecommendationReasonTemplateLibrary;
import com.ecommerce.recommendation.RecommendationRealtimeCacheService;
import com.ecommerce.recommendation.UserPreferenceBootstrapService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.RecommendationService;
import com.ecommerce.service.SeckillService;
import com.ecommerce.service.StreamRealtimeFeatureService;
import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationServiceImpl.class);
    private static final int MAX_LIMIT = 50;
    private static final int SNAPSHOT_FETCH_MULTIPLIER = 3;
    private static final int DEFAULT_METRIC_DAYS = 30;
    private static final int MAX_METRIC_DAYS = 365;
    private static final int ATTRIBUTION_LOOKBACK_DAYS = 30;
    private static final int SNAPSHOT_BYPASS_PURCHASE_HOURS = 24;
    private static final int RECENT_PURCHASE_CATEGORY_LOOKBACK_DAYS = 30;
    private static final int RECENT_PURCHASE_EVENT_LIMIT = 30;
    private static final int RECENT_PURCHASE_CATEGORY_LIMIT = 3;
    private static final double BEHAVIOR_WEIGHT_PURCHASE = 8.0;
    private static final double BEHAVIOR_WEIGHT_FAVORITE = 3.0;
    private static final double BEHAVIOR_WEIGHT_CART = 2.0;
    private static final double BEHAVIOR_WEIGHT_VIEW = 1.0;
    private static final String FLOW_SCENE_HOME = "home";
    private static final String FLOW_SCENE_SEARCH = "search";
    private static final String FLOW_SCENE_DETAIL = "detail";
    private static final String SESSION_INTENT_BROWSE = "browse";
    private static final String SESSION_INTENT_TARGET_SEARCH = "target_search";
    private static final String SESSION_INTENT_PRICE_COMPARE = "price_compare";
    private static final int ANTI_FATIGUE_MIN_PRODUCT_SIZE = 4;
    private static final String TASK_STATUS_SUCCESS = "success";
    private static final String SCENE_PERSONAL = "personal";
    private static final String SCENE_GUESS_YOU_LIKE = "guess_you_like";
    private static final String SCENE_HOT = "hot";
    private static final String SCENE_SIMILAR = "similar";
    private static final String SCENE_COLLABORATIVE_FILTERING = "collaborative_filtering";
    private static final String SOURCE_SNAPSHOT = "snapshot";
    private static final String SOURCE_LIVE = "live";
    private static final String ALGO_HYBRID_LIVE_CF = "hybrid_live_cf";
    private static final String ALGO_HYBRID_LIVE_NO_CF = "hybrid_live_no_cf";

    @Autowired
    private HybridRecommendationEngine hybridEngine;

    @Autowired
    private CollaborativeFiltering collaborativeFiltering;

    @Autowired
    private com.ecommerce.recommendation.RecommendationExplainer recommendationExplainer;

    @Autowired
    private ProductService productService;

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RecommendationRealtimeCacheService recommendationRealtimeCacheService;

    @Autowired
    private RecommendationAttributionService recommendationAttributionService;

    @Autowired
    private RecommendationRecallService recommendationRecallService;

    @Autowired
    private RecommendationRerankService recommendationRerankService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RecommendationReasonTemplateLibrary recommendationReasonTemplateLibrary;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserBehaviorMapper behaviorMapper;

    @Autowired
    private RecommendationEventMapper recommendationEventMapper;

    @Autowired
    private AnalyticsRecommendationResultMapper analyticsRecommendationResultMapper;

    @Autowired
    private AnalyticsRecommendationExposureMapper analyticsRecommendationExposureMapper;

    @Autowired
    private AnalyticsProductSimilarityMapper analyticsProductSimilarityMapper;

    @Autowired
    private AnalyticsKmeansTaskMapper analyticsKmeansTaskMapper;

    @Autowired
    private AnalyticsKmeansUserResultMapper analyticsKmeansUserResultMapper;

    @Autowired
    private AnalyticsKmeansSegmentMapper analyticsKmeansSegmentMapper;

    @Autowired
    private ABTestFramework abTestFramework;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private StreamRealtimeFeatureService streamRealtimeFeatureService;

    @Autowired
    private UserPreferenceBootstrapService userPreferenceBootstrapService;

    @Autowired(required = false)
    private com.ecommerce.service.StreamRealtimeRedisSinkService streamRealtimeRedisSinkService;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StreamRealtimeProjectionService streamRealtimeProjectionService;

    @Value("${recommendation.live-priority.personal-enabled:true}")
    private boolean livePriorityPersonalEnabled;

    @Value("${recommendation.live-priority.guess-you-like-enabled:true}")
    private boolean livePriorityGuessYouLikeEnabled;

    @Value("${recommendation.rerank.exposure-lookback-hours:12}")
    private int exposureLookbackHours;

    @Value("${recommendation.rerank.exposure-max-history:200}")
    private int exposureMaxHistory;

    @Value("${recommendation.rerank.exploration-base-rate:0.05}")
    private double explorationBaseRate;

    @Value("${recommendation.rerank.exploration-cold-start-bonus:0.03}")
    private double explorationColdStartBonus;

    @Value("${recommendation.rerank.max-explore-slots:2}")
    private int maxExploreSlots;

    @Value("${recommendation.rerank.category-saturation-penalty:9.0}")
    private double categorySaturationPenalty;

    @Value("${recommendation.rerank.novelty-bonus:14.0}")
    private double noveltyBonus;

    @Value("${recommendation.rerank.guardrail.default.max-per-category:2}")
    private int guardrailDefaultMaxPerCategory;

    @Value("${recommendation.rerank.guardrail.default.max-per-merchant:2}")
    private int guardrailDefaultMaxPerMerchant;

    @Value("${recommendation.rerank.guardrail.default.max-per-near-duplicate:1}")
    private int guardrailDefaultMaxPerNearDuplicate;

    @Value("${recommendation.rerank.guardrail.default.strict-window-size:8}")
    private int guardrailDefaultStrictWindowSize;

    @Value("${recommendation.rerank.guardrail.default.supplement-multiplier:4}")
    private int guardrailDefaultSupplementMultiplier;

    @Value("${recommendation.rerank.guardrail.home.max-per-category:2}")
    private int guardrailHomeMaxPerCategory;

    @Value("${recommendation.rerank.guardrail.home.max-per-merchant:2}")
    private int guardrailHomeMaxPerMerchant;

    @Value("${recommendation.rerank.guardrail.home.max-per-near-duplicate:1}")
    private int guardrailHomeMaxPerNearDuplicate;

    @Value("${recommendation.rerank.guardrail.home.strict-window-size:8}")
    private int guardrailHomeStrictWindowSize;

    @Value("${recommendation.rerank.guardrail.home.supplement-multiplier:4}")
    private int guardrailHomeSupplementMultiplier;

    @Value("${recommendation.rerank.guardrail.search.max-per-category:3}")
    private int guardrailSearchMaxPerCategory;

    @Value("${recommendation.rerank.guardrail.search.max-per-merchant:3}")
    private int guardrailSearchMaxPerMerchant;

    @Value("${recommendation.rerank.guardrail.search.max-per-near-duplicate:2}")
    private int guardrailSearchMaxPerNearDuplicate;

    @Value("${recommendation.rerank.guardrail.search.strict-window-size:10}")
    private int guardrailSearchStrictWindowSize;

    @Value("${recommendation.rerank.guardrail.search.supplement-multiplier:3}")
    private int guardrailSearchSupplementMultiplier;

    @Value("${recommendation.rerank.guardrail.detail.max-per-category:2}")
    private int guardrailDetailMaxPerCategory;

    @Value("${recommendation.rerank.guardrail.detail.max-per-merchant:2}")
    private int guardrailDetailMaxPerMerchant;

    @Value("${recommendation.rerank.guardrail.detail.max-per-near-duplicate:1}")
    private int guardrailDetailMaxPerNearDuplicate;

    @Value("${recommendation.rerank.guardrail.detail.strict-window-size:6}")
    private int guardrailDetailStrictWindowSize;

    @Value("${recommendation.rerank.guardrail.detail.supplement-multiplier:3}")
    private int guardrailDetailSupplementMultiplier;

    @Value("${recommendation.rerank.session-dedup.enabled:true}")
    private boolean sessionDedupEnabled;

    @Value("${recommendation.rerank.session-dedup.lookback-minutes:30}")
    private int sessionDedupLookbackMinutes;

    @Value("${recommendation.rerank.session-dedup.history-limit:300}")
    private int sessionDedupHistoryLimit;

    @Value("${recommendation.rerank.session-dedup.max-product-exposure:1}")
    private int sessionDedupMaxProductExposure;

    @Value("${recommendation.rerank.session-dedup.max-category-exposure:4}")
    private int sessionDedupMaxCategoryExposure;

    @Value("${recommendation.rerank.session-dedup.max-merchant-exposure:4}")
    private int sessionDedupMaxMerchantExposure;

    @Value("${recommendation.dislike.lookback-days:30}")
    private int dislikeLookbackDays;

    @Value("${recommendation.dislike.max-history:200}")
    private int dislikeMaxHistory;

    @Value("${recommendation.dislike.category-suppress-threshold:3}")
    private int dislikeCategorySuppressThreshold;

    @Value("${recommendation.dislike.min-remaining:6}")
    private int dislikeMinRemaining;

    @Value("${recommendation.rerank.fast-negative.enabled:true}")
    private boolean fastNegativeEnabled;

    @Value("${recommendation.rerank.fast-negative.lookback-minutes:10}")
    private int fastNegativeLookbackMinutes;

    @Value("${recommendation.rerank.fast-negative.max-history:240}")
    private int fastNegativeMaxHistory;

    @Value("${recommendation.rerank.fast-negative.short-dwell-seconds:6}")
    private int fastNegativeShortDwellSeconds;

    @Value("${recommendation.rerank.fast-negative.product-penalty:45.0}")
    private double fastNegativeProductPenalty;

    @Value("${recommendation.rerank.fast-negative.category-penalty:20.0}")
    private double fastNegativeCategoryPenalty;

    @Value("${recommendation.rerank.fast-negative.dislike-extra-penalty:35.0}")
    private double fastNegativeDislikeExtraPenalty;

    @Value("${recommendation.rerank.quality-gate.enabled:true}")
    private boolean qualityGateEnabled;

    @Value("${recommendation.rerank.quality-gate.lookback-days:30}")
    private int qualityGateLookbackDays;

    @Value("${recommendation.rerank.quality-gate.max-history:6000}")
    private int qualityGateMaxHistory;

    @Value("${recommendation.rerank.quality-gate.min-order-sample:3}")
    private int qualityGateMinOrderSample;

    @Value("${recommendation.rerank.quality-gate.high-refund-rate-threshold:0.22}")
    private double qualityGateHighRefundRateThreshold;

    @Value("${recommendation.rerank.quality-gate.low-rating-threshold:3.8}")
    private double qualityGateLowRatingThreshold;

    @Value("${recommendation.rerank.quality-gate.low-stock-threshold:8}")
    private int qualityGateLowStockThreshold;

    @Value("${recommendation.rerank.quality-gate.refund-penalty:65.0}")
    private double qualityGateRefundPenalty;

    @Value("${recommendation.rerank.quality-gate.rating-penalty:34.0}")
    private double qualityGateRatingPenalty;

    @Value("${recommendation.rerank.quality-gate.stock-penalty:28.0}")
    private double qualityGateStockPenalty;

    @Value("${recommendation.rerank.quality-gate.high-value-aov-threshold:500.0}")
    private double qualityGateHighValueAovThreshold;

    @Value("${recommendation.rerank.quality-gate.high-value-penalty-multiplier:1.35}")
    private double qualityGateHighValuePenaltyMultiplier;

    @Value("${recommendation.rerank.session-intent.enabled:true}")
    private boolean sessionIntentEnabled;

    @Value("${recommendation.rerank.session-intent.lookback-minutes:45}")
    private int sessionIntentLookbackMinutes;

    @Value("${recommendation.rerank.session-intent.max-history:160}")
    private int sessionIntentMaxHistory;

    @Value("${recommendation.rerank.session-intent.price-compare-min-products:3}")
    private int sessionIntentPriceCompareMinProducts;

    @Value("${recommendation.rerank.session-intent.price-compare-cv-threshold:0.35}")
    private double sessionIntentPriceCompareCvThreshold;

    @Value("${recommendation.rerank.exploration-dynamic.enabled:true}")
    private boolean explorationDynamicEnabled;

    @Value("${recommendation.rerank.exploration-dynamic.low-entropy-threshold:0.45}")
    private double explorationLowEntropyThreshold;

    @Value("${recommendation.rerank.exploration-dynamic.low-entropy-bonus:0.10}")
    private double explorationLowEntropyBonus;

    @Value("${recommendation.rerank.exploration-dynamic.new-user-bonus:0.10}")
    private double explorationNewUserBonus;

    @Value("${recommendation.rerank.exploration-dynamic.high-intent-penalty:0.12}")
    private double explorationHighIntentPenalty;

    @Value("${recommendation.rerank.exploration-dynamic.compare-intent-penalty:0.06}")
    private double explorationCompareIntentPenalty;

    @Value("${recommendation.rerank.exploration-dynamic.new-user-behavior-threshold:12}")
    private int explorationNewUserBehaviorThreshold;

    @Value("${recommendation.rerank.multi-objective.ctr-weight:0.26}")
    private double multiObjectiveCtrWeight;

    @Value("${recommendation.rerank.multi-objective.order-rate-weight:0.30}")
    private double multiObjectiveOrderRateWeight;

    @Value("${recommendation.rerank.multi-objective.aov-weight:0.14}")
    private double multiObjectiveAovWeight;

    @Value("${recommendation.rerank.multi-objective.gmv-weight:0.14}")
    private double multiObjectiveGmvWeight;

    @Value("${recommendation.rerank.multi-objective.diversity-weight:0.20}")
    private double multiObjectiveDiversityWeight;

    @Value("${recommendation.rerank.multi-objective.refund-penalty-weight:0.10}")
    private double multiObjectiveRefundPenaltyWeight;

    @Value("${recommendation.rerank.multi-objective.boost-scale:180.0}")
    private double multiObjectiveBoostScale;

    @Value("${recommendation.rerank.multi-objective.metric-lookback-days:14}")
    private int multiObjectiveMetricLookbackDays;

    @Value("${recommendation.rerank.multi-objective.metric-max-rows:4000}")
    private int multiObjectiveMetricMaxRows;

    @Value("${recommendation.rerank.multi-objective.min-exposure-sample:12}")
    private int multiObjectiveMinExposureSample;

    @Value("${recommendation.rerank.auto-rollback.enabled:true}")
    private boolean rerankAutoRollbackEnabled;

    @Value("${recommendation.rerank.auto-rollback.check-interval-seconds:60}")
    private int rerankAutoRollbackCheckIntervalSeconds;

    @Value("${recommendation.rerank.auto-rollback.refund-rate-threshold:18.0}")
    private double rerankAutoRollbackRefundRateThreshold;

    @Value("${recommendation.rerank.auto-rollback.retention7d-floor:9.0}")
    private double rerankAutoRollbackRetention7dFloor;

    @Value("${recommendation.rerank.auto-rollback.repurchase-rate-floor:7.0}")
    private double rerankAutoRollbackRepurchaseRateFloor;

    @Value("${recommendation.rerank.narrow-guard.enabled:true}")
    private boolean narrowGuardEnabled;

    @Value("${recommendation.rerank.narrow-guard.ab-enabled:true}")
    private boolean narrowGuardAbEnabled;

    @Value("${recommendation.rerank.narrow-guard.ab-treatment-groups:hybrid,cf_heavy}")
    private String narrowGuardTreatmentGroups;

    @Value("${recommendation.rerank.narrow-guard.min-category-entropy:0.62}")
    private double narrowGuardMinCategoryEntropy;

    @Value("${recommendation.rerank.narrow-guard.max-single-category-ratio:0.68}")
    private double narrowGuardMaxSingleCategoryRatio;

    @Value("${recommendation.rerank.narrow-guard.protected-head-size:3}")
    private int narrowGuardProtectedHeadSize;

    @Value("${recommendation.governance.event-idempotency-ttl-seconds:86400}")
    private long recommendationEventIdempotencyTtlSeconds;

    @Value("${recommendation.rerank.narrow-guard.max-adjust-rounds:48}")
    private int narrowGuardMaxAdjustRounds;

    private volatile LocalDateTime rerankRollbackLastCheckTime;
    private volatile boolean rerankRollbackActive;
    private volatile String rerankRollbackReason = "";

    @Override
    public List<Product> getPersonalRecommendations(Long userId, int limit) {
        ensureUserPreferenceReady(userId);
        int safeLimit = normalizeLimit(limit);
        ClusterContext clusterContext = loadClusterContext(userId);
        boolean freshSignalPreferred = shouldPreferLiveRecommendations(userId);
        boolean livePriorityEnabled = livePriorityPersonalEnabled || freshSignalPreferred;
        LiveRecommendationDecision liveDecision = null;
        if (livePriorityEnabled) {
            liveDecision = buildPersonalLiveDecision(userId, clusterContext, safeLimit);
        }
        if (liveDecision != null && !liveDecision.products.isEmpty()) {
            return attachExposureTracking(
                    liveDecision.products,
                    userId,
                    SCENE_PERSONAL,
                    SOURCE_LIVE,
                    clusterContext,
                    Collections.emptyMap(),
                    liveDecision.algorithmTag,
                    null);
        }
        List<AnalyticsRecommendationResult> snapshotRows = loadRecommendationSnapshotRows(
                SCENE_PERSONAL, userId, safeLimit);
        if (!snapshotRows.isEmpty()) {
            List<Product> snapshotProducts = mapAvailableProductsByIds(
                    extractProductIds(snapshotRows), getSnapshotFetchLimit(safeLimit));
            snapshotProducts = applyClusterAwareRanking(snapshotProducts, clusterContext, safeLimit);
            if (liveDecision == null) {
                liveDecision = buildPersonalLiveDecision(userId, clusterContext, safeLimit);
            }
            List<Product> result = snapshotProducts.size() >= safeLimit
                    ? snapshotProducts
                    : appendFallbackProducts(
                    snapshotProducts,
                    liveDecision.products,
                    safeLimit);
            return attachExposureTracking(
                    result,
                    userId,
                    SCENE_PERSONAL,
                    SOURCE_SNAPSHOT,
                    clusterContext,
                    buildSnapshotRowMap(snapshotRows),
                    "hybrid_snapshot",
                    resolveSnapshotModelVersion(snapshotRows));
        }
        if (liveDecision == null) {
            liveDecision = buildPersonalLiveDecision(userId, clusterContext, safeLimit);
        }
        return attachExposureTracking(
                liveDecision.products,
                userId,
                SCENE_PERSONAL,
                SOURCE_LIVE,
                clusterContext,
                Collections.emptyMap(),
                liveDecision.algorithmTag,
                null);
    }

    @Override
    public List<Product> getSimilarProducts(Long userId, Long productId, int limit) {
        int safeLimit = normalizeLimit(limit);
        List<Product> snapshotProducts = loadSimilaritySnapshotProducts(productId, safeLimit);
        if (!snapshotProducts.isEmpty()) {
            List<Product> result = snapshotProducts.size() >= safeLimit
                    ? snapshotProducts
                    : appendFallbackProducts(snapshotProducts, getSimilarProductsLive(productId, safeLimit), safeLimit);
            return attachExposureTracking(
                    result,
                    userId,
                    SCENE_SIMILAR,
                    SOURCE_SNAPSHOT,
                    loadClusterContext(userId),
                    Collections.emptyMap(),
                    "similar_snapshot",
                    null);
        }
        return attachExposureTracking(
                getSimilarProductsLive(productId, safeLimit),
                userId,
                SCENE_SIMILAR,
                SOURCE_LIVE,
                loadClusterContext(userId),
                Collections.emptyMap(),
                "similar_live",
                null);
    }

    @Override
    public List<Product> getHotRecommendations(Long userId, int limit) {
        ensureUserPreferenceReady(userId);
        int safeLimit = normalizeLimit(limit);
        ClusterContext clusterContext = loadClusterContext(userId);
        if (hasRealtimeHotSignal()) {
            List<Product> liveResult = applyHomeRecommendationDiversity(
                    getHotRecommendationsLive(userId, clusterContext, safeLimit),
                    safeLimit,
                    SCENE_HOT);
            return attachExposureTracking(
                    liveResult,
                    userId,
                    SCENE_HOT,
                    SOURCE_LIVE,
                    clusterContext,
                    Collections.emptyMap(),
                    "hot_live_stream",
                    null);
        }
        List<AnalyticsRecommendationResult> snapshotRows = loadRecommendationSnapshotRows(SCENE_HOT, 0L, safeLimit);
        if (!snapshotRows.isEmpty()) {
            List<Product> snapshotProducts = mapAvailableProductsByIds(
                    extractProductIds(snapshotRows), getSnapshotFetchLimit(safeLimit));
            snapshotProducts = applyClusterAwareRanking(snapshotProducts, clusterContext, getSnapshotFetchLimit(safeLimit));
            List<Product> result = snapshotProducts.size() >= safeLimit
                    ? snapshotProducts
                    : appendFallbackProducts(
                    snapshotProducts,
                    getHotRecommendationsLive(userId, clusterContext, safeLimit),
                    safeLimit);
            result = applyHomeRecommendationDiversity(result, safeLimit, SCENE_HOT);
            return attachExposureTracking(
                    result,
                    userId,
                    SCENE_HOT,
                    SOURCE_SNAPSHOT,
                    clusterContext,
                    buildSnapshotRowMap(snapshotRows),
                    "hot_snapshot",
                    resolveSnapshotModelVersion(snapshotRows));
        }
        List<Product> liveResult = applyHomeRecommendationDiversity(
                getHotRecommendationsLive(userId, clusterContext, safeLimit),
                safeLimit,
                SCENE_HOT);
        return attachExposureTracking(
                liveResult,
                userId,
                SCENE_HOT,
                SOURCE_LIVE,
                clusterContext,
                Collections.emptyMap(),
                "hot_live",
                null);
    }

    @Override
    public List<Product> rerankFeedRecommendations(Long userId,
                                                   List<Product> products,
                                                   int limit,
                                                   String scene,
                                                   String algorithmTag) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = normalizeLimit(limit);
        String feedScene = normalizeFeedScene(scene);
        ClusterContext clusterContext = loadClusterContext(userId);
        List<Product> candidates = normalizeRecommendationProducts(products, Math.max(products.size(), safeLimit * 3));
        candidates = deduplicateProducts(candidates);
        candidates = applyFeedDiversityByScene(candidates, safeLimit, feedScene);
        return attachExposureTracking(
                candidates,
                userId,
                feedScene,
                SOURCE_LIVE,
                clusterContext,
                Collections.emptyMap(),
                firstNonEmpty(algorithmTag, "home_algorithm"),
                null);
    }

    /**
     * 从所有分类中轮流随机取商品，保证每次结果不同且分类多样
     */
    private List<Product> getDiverseRecommendations(int limit) {
        int safeLimit = normalizeLimit(limit);

        List<Map<String, Object>> categories = productMapper.selectAllCategoryIds();
        if (categories.isEmpty()) {
            List<Product> fallback = normalizeRecommendationProducts(productService.getHotProducts(safeLimit * 3), safeLimit * 3);
            Collections.shuffle(fallback);
            return fallback.subList(0, Math.min(safeLimit, fallback.size()));
        }

        Collections.shuffle(categories);

        int perCategory = Math.max(2, (safeLimit / categories.size()) + 1);
        List<Product> pool = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (Map<String, Object> category : categories) {
            Long categoryId = ((Number) category.get("categoryId")).longValue();
            List<Product> categoryProducts = productMapper.selectRandomByCategory(categoryId, perCategory);
            for (Product product : categoryProducts) {
                if (seen.add(product.getId())) {
                    pool.add(product);
                }
            }
        }

        return ensureCategoryDiversity(pool, safeLimit);
    }

    private List<Product> ensureCategoryDiversity(List<Product> candidates, int limit) {
        return recommendationRerankService.ensureCategoryDiversity(candidates, limit);
    }

    /**
     * 流量护栏:
     * 1) 单类目上限;
     * 2) 单商家上限;
     * 3) 近重复上限;
     * 4) 按场景(home/search/detail)使用不同阈值。
     */
    private List<Product> applyHomeRecommendationDiversity(List<Product> candidates, int limit, String scene) {
        SceneGuardrailConfig guardrailConfig = resolveGuardrailConfig(scene);
        int supplementLimit = Math.max(Math.max(1, limit) * Math.max(2, guardrailConfig.supplementMultiplier), 20);
        return recommendationRerankService.applySceneGuardrails(
                candidates,
                limit,
                toGuardrailConfig(guardrailConfig),
                () -> getDiverseRecommendations(supplementLimit));
    }

    private List<Product> applyHomeRecommendationDiversity(List<Product> candidates, int limit) {
        return applyHomeRecommendationDiversity(candidates, limit, FLOW_SCENE_HOME);
    }

    private SceneGuardrailConfig resolveGuardrailConfig(String scene) {
        String flowScene = resolveFlowScene(scene);
        if (FLOW_SCENE_SEARCH.equals(flowScene)) {
            return new SceneGuardrailConfig(
                    guardrailSearchMaxPerCategory,
                    guardrailSearchMaxPerMerchant,
                    guardrailSearchMaxPerNearDuplicate,
                    guardrailSearchStrictWindowSize,
                    guardrailSearchSupplementMultiplier);
        }
        if (FLOW_SCENE_DETAIL.equals(flowScene)) {
            return new SceneGuardrailConfig(
                    guardrailDetailMaxPerCategory,
                    guardrailDetailMaxPerMerchant,
                    guardrailDetailMaxPerNearDuplicate,
                    guardrailDetailStrictWindowSize,
                    guardrailDetailSupplementMultiplier);
        }
        if (FLOW_SCENE_HOME.equals(flowScene)) {
            return new SceneGuardrailConfig(
                    guardrailHomeMaxPerCategory,
                    guardrailHomeMaxPerMerchant,
                    guardrailHomeMaxPerNearDuplicate,
                    guardrailHomeStrictWindowSize,
                    guardrailHomeSupplementMultiplier);
        }
        return new SceneGuardrailConfig(
                guardrailDefaultMaxPerCategory,
                guardrailDefaultMaxPerMerchant,
                guardrailDefaultMaxPerNearDuplicate,
                guardrailDefaultStrictWindowSize,
                guardrailDefaultSupplementMultiplier);
    }

    private RecommendationRerankService.GuardrailConfig toGuardrailConfig(SceneGuardrailConfig config) {
        if (config == null) {
            return RecommendationRerankService.GuardrailConfig.defaults();
        }
        return new RecommendationRerankService.GuardrailConfig(
                config.maxPerCategory,
                config.maxPerMerchant,
                config.maxPerNearDuplicate,
                config.strictWindowSize,
                config.supplementMultiplier);
    }

    private RecommendationRerankService.RankingContext toRankingContext(ClusterContext clusterContext) {
        if (clusterContext == null) {
            return null;
        }
        return new RecommendationRerankService.RankingContext(
                clusterContext.topCategories,
                clusterContext.topTags,
                clusterContext.avgPricePerOrder);
    }

    private String resolveFlowScene(String scene) {
        String normalized = normalizeText(scene);
        if (!StringUtils.hasText(normalized)) {
            return FLOW_SCENE_HOME;
        }
        if (normalized.contains("search")) {
            return FLOW_SCENE_SEARCH;
        }
        if (normalized.contains("detail")
                || normalized.contains("similar")
                || SCENE_SIMILAR.equals(normalized)) {
            return FLOW_SCENE_DETAIL;
        }
        if (normalized.contains("home")
                || isFeedScene(normalized)
                || SCENE_HOT.equals(normalized)
                || SCENE_PERSONAL.equals(normalized)
                || SCENE_GUESS_YOU_LIKE.equals(normalized)) {
            return FLOW_SCENE_HOME;
        }
        return FLOW_SCENE_HOME;
    }

    private String normalizeFeedScene(String scene) {
        String normalized = normalizeText(scene);
        if (!StringUtils.hasText(normalized)) {
            return SCENE_GUESS_YOU_LIKE;
        }
        if ("cf".equals(normalized) || "collaborative".equals(normalized)) {
            return SCENE_COLLABORATIVE_FILTERING;
        }
        if ("hybrid".equals(normalized) || "home".equals(normalized) || "recommend".equals(normalized)) {
            return SCENE_GUESS_YOU_LIKE;
        }
        return normalized;
    }

    @Override
    public List<Product> guessYouLike(Long userId, int limit) {
        return guessYouLike(userId, limit, false);
    }

    @Override
    public List<Product> guessYouLike(Long userId, int limit, boolean forcePersonalized) {
        ensureUserPreferenceReady(userId);
        int safeLimit = normalizeLimit(limit);
        ClusterContext clusterContext = loadClusterContext(userId);
        boolean freshSignalPreferred = shouldPreferLiveRecommendations(userId);

        boolean isControlGroup = false;
        if (moduleSwitchService.isEnabled("ab-test") && userId != null) {
            try {
                isControlGroup = "CONTROL".equalsIgnoreCase(abTestFramework.assignGroup(userId).code);
            } catch (Exception ignored) {}
        }
        if (forcePersonalized) {
            isControlGroup = false;
        }

        if (isControlGroup) {
            List<Product> liveResult = applyHomeRecommendationDiversity(
                    getHotRecommendationsLive(userId, clusterContext, safeLimit),
                    safeLimit,
                    SCENE_GUESS_YOU_LIKE);
            return attachExposureTracking(
                    liveResult,
                    userId,
                    SCENE_GUESS_YOU_LIKE,
                    SOURCE_LIVE,
                    clusterContext,
                    Collections.emptyMap(),
                    "ab_control_hot",
                    null);
        }

        boolean livePriorityEnabled = forcePersonalized || livePriorityGuessYouLikeEnabled || freshSignalPreferred;
        LiveRecommendationDecision liveDecision = null;
        if (livePriorityEnabled) {
            liveDecision = buildGuessYouLikeLiveDecision(userId, clusterContext, safeLimit);
        }
        if (liveDecision != null && !liveDecision.products.isEmpty()) {
            return attachExposureTracking(
                    liveDecision.products,
                    userId,
                    SCENE_GUESS_YOU_LIKE,
                    SOURCE_LIVE,
                    clusterContext,
                    Collections.emptyMap(),
                    liveDecision.algorithmTag,
                    null);
        }

        List<AnalyticsRecommendationResult> snapshotRows = loadRecommendationSnapshotRows(
                SCENE_GUESS_YOU_LIKE, userId, safeLimit);
        if (!snapshotRows.isEmpty()) {
            List<Product> snapshotProducts = mapAvailableProductsByIds(
                    extractProductIds(snapshotRows), getSnapshotFetchLimit(safeLimit));
            snapshotProducts = applyClusterAwareRanking(snapshotProducts, clusterContext, getSnapshotFetchLimit(safeLimit));
            if (liveDecision == null) {
                liveDecision = buildGuessYouLikeLiveDecision(userId, clusterContext, safeLimit);
            }
            List<Product> result = snapshotProducts.size() >= safeLimit
                    ? snapshotProducts
                    : appendFallbackProducts(
                    snapshotProducts,
                    liveDecision.products,
                    safeLimit);
            result = applyHomeRecommendationDiversity(result, safeLimit, SCENE_GUESS_YOU_LIKE);
            return attachExposureTracking(
                    result,
                    userId,
                    SCENE_GUESS_YOU_LIKE,
                    SOURCE_SNAPSHOT,
                    clusterContext,
                    buildSnapshotRowMap(snapshotRows),
                    "hybrid_snapshot",
                    resolveSnapshotModelVersion(snapshotRows));
        }
        if (liveDecision == null) {
            liveDecision = buildGuessYouLikeLiveDecision(userId, clusterContext, safeLimit);
        }
        return attachExposureTracking(
                liveDecision.products,
                userId,
                SCENE_GUESS_YOU_LIKE,
                SOURCE_LIVE,
                clusterContext,
                Collections.emptyMap(),
                liveDecision.algorithmTag,
                null);
    }

    @Override
    public Map<String, Object> getPersonalRecommendationsWithExplanation(Long userId, int limit) {
        return getPersonalRecommendationsWithExplanation(userId, limit, false);
    }

    @Override
    public Map<String, Object> getPersonalRecommendationsWithExplanation(Long userId,
                                                                         int limit,
                                                                         boolean forcePersonalized) {
        ensureUserPreferenceReady(userId);
        int safeLimit = normalizeLimit(limit);
        ClusterContext clusterContext = loadClusterContext(userId);
        boolean freshSignalPreferred = shouldPreferLiveRecommendations(userId);
        boolean preferLive = forcePersonalized || livePriorityPersonalEnabled || freshSignalPreferred;
        List<AnalyticsRecommendationResult> snapshotRows = preferLive
                ? Collections.emptyList()
                : loadRecommendationSnapshotRows(SCENE_PERSONAL, userId, safeLimit);
        if (!snapshotRows.isEmpty()) {
            List<Product> products = mapAvailableProductsByIds(
                    extractProductIds(snapshotRows), getSnapshotFetchLimit(safeLimit));
            products = applyClusterAwareRanking(products, clusterContext, safeLimit);
            products = applyFeedDiversityByScene(products, safeLimit, SCENE_PERSONAL);
            products = enforceTopPreferenceCoverage(products, userId, safeLimit);
            if (!products.isEmpty()) {
                Map<String, Object> result = buildSnapshotExplanationResponse(products, snapshotRows);
                enrichExplanationWithCluster(result, clusterContext);
                enrichRecommendationNarrative(result, products, clusterContext);
                return result;
            }
        }

        Map<String, Object> fallback = hybridEngine.recommendWithExplanation(userId, safeLimit);
        List<Long> productIds = extractLongIds(fallback.get("productIds"));
        List<Product> products = mapAvailableProductsByIds(productIds, getSnapshotFetchLimit(safeLimit));
        products = applyClusterAwareRanking(products, clusterContext, safeLimit);
        products = applyFeedDiversityByScene(products, safeLimit, SCENE_PERSONAL);
        products = enforceTopPreferenceCoverage(products, userId, safeLimit);
        List<Long> filteredProductIds = products.stream().map(Product::getId).collect(Collectors.toList());
        List<Map<String, Object>> alignedExplanations = alignExplanations(fallback.get("explanations"), filteredProductIds);
        alignedExplanations = mergeProductReasonsIntoExplanations(products, alignedExplanations);
        fallback.put("productIds", filteredProductIds);
        fallback.put("products", products);
        fallback.put("explanations", alignedExplanations);
        fallback.put("sourceType", SOURCE_LIVE);
        if (freshSignalPreferred) {
            fallback.put("freshBehaviorBoost", true);
        }
        String modelVersion = stringValue(fallback.get("modelVersion"));
        if (!StringUtils.hasText(modelVersion)) {
            modelVersion = "stream-live-v1";
            fallback.put("modelVersion", modelVersion);
        }
        enrichProductsWithExplanationMetadata(
                products, alignedExplanations, SCENE_PERSONAL, SOURCE_LIVE, modelVersion, null, userId);
        fallback.put("dataFreshness", products.isEmpty() ? "实时" : firstNonEmpty(products.get(0).getDataFreshness(), "实时"));
        enrichExplanationWithCluster(fallback, clusterContext);
        enrichRecommendationNarrative(fallback, products, clusterContext);
        return fallback;
    }

    @Override
    public Map<String, Object> getRealtimeRecommendationDashboard(Long userId, int limit) {
        return getRealtimeRecommendationDashboard(userId, limit, false);
    }

    @Override
    public Map<String, Object> getRealtimeRecommendationDashboard(Long userId,
                                                                  int limit,
                                                                  boolean forcePersonalized) {
        if (userId == null || userId <= 0) {
            return Collections.emptyMap();
        }
        ensureUserPreferenceReady(userId);

        int safeLimit = normalizeLimit(limit);
        AnalyticsKmeansTask latestTask = findLatestSuccessfulTaskRecord();
        ClusterContext clusterContext = loadClusterContext(userId);
        Map<String, Object> recommendationPayload = getPersonalRecommendationsWithExplanation(
                userId, safeLimit, forcePersonalized);
        Map<String, Object> profilePayload = buildRealtimeProfile(userId, recommendationPayload, forcePersonalized);
        Map<String, Object> segmentPayload = buildRealtimeSegment(userId, latestTask, clusterContext);
        Map<String, Object> dataSourcePayload = buildRealtimeDataSource(recommendationPayload, latestTask);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("generatedAt", LocalDateTime.now());
        payload.put("refreshMode", "request_time_recommendation_plus_latest_segment_snapshot");
        payload.put("profile", profilePayload);
        payload.put("segment", segmentPayload);
        payload.put("recommendation", recommendationPayload);
        payload.put("dataSource", dataSourcePayload);
        payload.put("forcePersonalized", forcePersonalized);
        payload.put("executiveSummary", buildRealtimeExecutiveSummary(
                profilePayload,
                segmentPayload,
                recommendationPayload,
                dataSourcePayload));
        return payload;
    }

    private Map<String, Object> buildRealtimeProfile(Long userId,
                                                     Map<String, Object> recommendationPayload,
                                                     boolean forcePersonalized) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("userId", userId);
        profile.put("username", resolveDisplayName(user));
        profile.put("avatar", user.getAvatar());
        List<Map<String, Object>> behaviorStats = behaviorMapper.selectUserBehaviorStats(userId);
        boolean realtimeFeatureEnriched = false;
        if (behaviorStats == null || behaviorStats.isEmpty()) {
            List<Map<String, Object>> realtimeBehaviorStats = streamRealtimeFeatureService.getUserBehaviorStats(userId);
            if (realtimeBehaviorStats != null && !realtimeBehaviorStats.isEmpty()) {
                behaviorStats = realtimeBehaviorStats;
                realtimeFeatureEnriched = true;
            }
        }
        profile.put("behaviorStats", behaviorStats == null ? Collections.emptyList() : behaviorStats);

        List<Map<String, Object>> preferences = behaviorMapper.selectUserPreferences(userId);
        List<Map<String, Object>> realtimePreferences = loadRealtimePreferenceRows(userId);
        List<Map<String, Object>> searchCategoryPreferences = loadSearchCategoryPreferenceRows(userId);
        if (preferences == null || preferences.isEmpty()) {
            preferences = realtimePreferences;
        }
        profile.put("preferences", preferences == null ? Collections.emptyList() : preferences);
        profile.put("searchCategoryPreferences", searchCategoryPreferences);

        Map<String, String> categoryNameMap = loadCategoryNameMap();
        Map<String, Double> categoryWeights = new LinkedHashMap<>();
        Set<String> userTags = new LinkedHashSet<>();
        for (Map<String, Object> preference : preferences) {
            Object categoryId = preference.get("category_id");
            Object weight = preference.get("weight");
            Object tags = preference.get("tags");
            if (categoryId != null && weight != null) {
                String key = String.valueOf(categoryId);
                String categoryName = categoryNameMap.getOrDefault(key, key);
                categoryWeights.merge(categoryName, parseDouble(weight), Double::sum);
            }
            collectTags(tags, userTags);
        }

        for (Map<String, Object> preference : realtimePreferences) {
            Object categoryName = firstNonNull(preference.get("category_name"), preference.get("categoryName"));
            Object categoryId = firstNonNull(preference.get("category_id"), preference.get("categoryId"));
            Object weight = firstNonNull(preference.get("weight"), preference.get("preferenceScore"));
            String categoryLabel = categoryName == null
                    ? (categoryId == null ? null : String.valueOf(categoryId))
                    : String.valueOf(categoryName);
            if (StringUtils.hasText(categoryLabel) && weight != null) {
                categoryWeights.merge(categoryLabel, parseDouble(weight), Math::max);
            }
        }
        if (!realtimePreferences.isEmpty()) {
            realtimeFeatureEnriched = true;
        }

        for (Map<String, Object> preference : searchCategoryPreferences) {
            Object categoryName = firstNonNull(preference.get("category_name"), preference.get("categoryName"));
            Object categoryId = firstNonNull(preference.get("category_id"), preference.get("categoryId"));
            Object weight = firstNonNull(preference.get("weight"), preference.get("preferenceScore"));
            String categoryLabel = categoryName == null
                    ? (categoryId == null ? null : categoryNameMap.getOrDefault(String.valueOf(categoryId), String.valueOf(categoryId)))
                    : String.valueOf(categoryName);
            if (StringUtils.hasText(categoryLabel) && weight != null) {
                categoryWeights.merge(categoryLabel, parseDouble(weight), Double::sum);
            }
        }
        if (!searchCategoryPreferences.isEmpty()) {
            realtimeFeatureEnriched = true;
        }

        Map<String, Double> realtimeCategoryWeights = streamRealtimeFeatureService.getUserCategoryWeights(userId);
        if (realtimeCategoryWeights != null && !realtimeCategoryWeights.isEmpty()) {
            for (Map.Entry<String, Double> entry : realtimeCategoryWeights.entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null || entry.getValue() <= 0D) {
                    continue;
                }
                categoryWeights.merge(entry.getKey(), entry.getValue(), Math::max);
            }
            realtimeFeatureEnriched = true;
        }

        Set<String> realtimeTags = streamRealtimeFeatureService.getUserTags(userId);
        if (realtimeTags != null && !realtimeTags.isEmpty()) {
            userTags.addAll(realtimeTags);
            realtimeFeatureEnriched = true;
        }

        Map<Long, Double> userVector = collaborativeFiltering.buildUserVector(userId);
        profile.put("interactedProducts", userVector.size());
        profile.put("vectorDimension", userVector.size());
        profile.put("categoryWeights", categoryWeights);
        profile.put("userTags", new ArrayList<>(userTags));
        profile.put("topCategories", categoryWeights.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .map(Map.Entry::getKey)
                .limit(3)
                .collect(Collectors.toList()));
        profile.put("topTags", userTags.stream().limit(5).collect(Collectors.toList()));
        profile.put("shoppingMomentum", buildShoppingMomentum(profile.get("behaviorStats")));
        profile.put("journeyStage", buildJourneyStage(categoryWeights, userTags, userVector.size()));
        profile.put("realtimeFeatureEnriched", realtimeFeatureEnriched);

        Map<String, Object> experiment = buildExperimentMeta(userId, forcePersonalized);
        profile.putAll(experiment);
        profile.put("recommendationSource", recommendationPayload == null ? null : recommendationPayload.get("sourceType"));
        profile.put("profileSummary", buildProfileSummary(categoryWeights, userTags));
        return profile;
    }

    private List<Map<String, Object>> loadSearchCategoryPreferenceRows(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> rows = behaviorMapper.selectUserSearchCategoryPreferences(userId, 8);
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception exception) {
            log.debug("[Recommendation] load search category preferences failed userId={}: {}",
                    userId,
                    exception.getMessage());
            return Collections.emptyList();
        }
    }

    private void ensureUserPreferenceReady(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        try {
            userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        } catch (Exception exception) {
            log.debug("[Recommendation] ensure user preference failed userId={}, fallback continue: {}",
                    userId,
                    exception.getMessage());
        }
    }

    private Map<String, Object> buildRealtimeSegment(Long userId,
                                                     AnalyticsKmeansTask latestTask,
                                                     ClusterContext clusterContext) {
        Map<String, Object> segmentPayload = new LinkedHashMap<>();
        if (latestTask == null) {
            segmentPayload.put("available", false);
            segmentPayload.put("message", "暂无可用分群批次，推荐仍会按实时行为刷新");
            return segmentPayload;
        }

        segmentPayload.put("available", true);
        segmentPayload.put("taskId", latestTask.getId());
        segmentPayload.put("batchNo", latestTask.getBatchNo());
        segmentPayload.put("snapshotDate", latestTask.getSnapshotDate());

        Map<String, Object> detail = analyticsKmeansUserResultMapper.selectUserClusterDetail(latestTask.getId(), userId);
        if (detail == null || detail.isEmpty()) {
            segmentPayload.put("segmentCode", clusterContext == null ? null : clusterContext.segmentCode);
            segmentPayload.put("segmentName", clusterContext == null ? null : clusterContext.segmentName);
            segmentPayload.put("message", "当前用户尚未进入最近一批分群结果，通常属于冷启动或新近注册");
            return segmentPayload;
        }

        String segmentCode = stringValue(detail.get("segmentCode"));
        AnalyticsKmeansSegment segment = analyticsKmeansSegmentMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsKmeansSegment>()
                        .eq(AnalyticsKmeansSegment::getTaskId, latestTask.getId())
                        .eq(AnalyticsKmeansSegment::getSegmentCode, segmentCode)
                        .last("LIMIT 1"));

        segmentPayload.put("segmentCode", segmentCode);
        segmentPayload.put("segmentName", resolveSegmentName(detail, segment, clusterContext));
        segmentPayload.put("personaSummary", firstNonEmpty(
                stringValue(detail.get("personaSummary")),
                segment == null ? null : segment.getLlmSummary(),
                "系统已基于订单、行为与偏好标签生成当前用户画像"));
        segmentPayload.put("confidenceScore", detail.get("confidenceScore"));
        segmentPayload.put("isColdStart", safeInt(parseInteger(detail.get("isColdStart"))));
        segmentPayload.put("distanceToCenter", detail.get("distanceToCenter"));
        segmentPayload.put("featureHighlights", buildFeatureHighlights(detail));
        segmentPayload.put("topCategories", segment != null && segment.getTopCategories() != null
                ? segment.getTopCategories()
                : (clusterContext == null ? Collections.emptyList() : clusterContext.topCategories));
        segmentPayload.put("topTags", segment != null && segment.getTopTags() != null
                ? segment.getTopTags()
                : (clusterContext == null ? Collections.emptyList() : clusterContext.topTags));
        segmentPayload.put("operationSuggestion", segment == null ? null : segment.getOperationSuggestion());
        segmentPayload.put("segmentDescription", segment == null ? null : segment.getSegmentDescription());
        segmentPayload.put("llmSummary", segment == null ? null : segment.getLlmSummary());
        segmentPayload.put("userCount", segment == null ? null : segment.getUserCount());
        segmentPayload.put("percentage", segment == null ? null : segment.getPercentage());
        segmentPayload.put("profileLabel", buildSegmentProfileLabel(clusterContext));
        segmentPayload.put("operationsFocus", buildSegmentOperationsFocus(segment, clusterContext));
        return segmentPayload;
    }

    private Map<String, Object> buildRealtimeDataSource(Map<String, Object> recommendationPayload,
                                                        AnalyticsKmeansTask latestTask) {
        Map<String, Object> dataSource = new LinkedHashMap<>();
        dataSource.put("recommendationSource", recommendationPayload == null
                ? SOURCE_LIVE
                : firstNonEmpty(stringValue(recommendationPayload.get("sourceType")), SOURCE_LIVE));
        dataSource.put("segmentSource", latestTask == null ? "none" : "latest_success_kmeans_task");
        dataSource.put("segmentSnapshotDate", latestTask == null ? null : latestTask.getSnapshotDate());
        dataSource.put("refreshRule", "recommendation_refreshes_on_every_request_segment_follows_latest_successful_batch");
        return dataSource;
    }

    private Map<String, Object> buildExperimentMeta(Long userId) {
        return buildExperimentMeta(userId, false);
    }

    private Map<String, Object> buildExperimentMeta(Long userId, boolean forcePersonalized) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (forcePersonalized) {
            meta.put("experimentGroup", "hybrid");
            meta.put("experimentGroupDesc", "标准混合组（按用户稳定分桶）");
            return meta;
        }
        if (!moduleSwitchService.isEnabled("ab-test")) {
            meta.put("experimentGroup", "disabled");
            meta.put("experimentGroupDesc", "A/B 实验当前关闭");
            return meta;
        }
        try {
            ABTestFramework.ExperimentGroup group = abTestFramework.assignGroup(userId);
            meta.put("experimentGroup", group.code);
            meta.put("experimentGroupDesc", group.description);
        } catch (Exception exception) {
            meta.put("experimentGroup", "disabled");
            meta.put("experimentGroupDesc", "A/B 分流读取失败，已回退到默认策略");
        }
        return meta;
    }

    private Map<String, String> loadCategoryNameMap() {
        Map<String, String> categoryNameMap = new LinkedHashMap<>();
        for (Map<String, Object> category : productMapper.selectAllCategoryIds()) {
            Object categoryId = category.get("categoryId");
            Object categoryName = category.get("categoryName");
            if (categoryId != null && categoryName != null) {
                categoryNameMap.put(String.valueOf(categoryId), String.valueOf(categoryName));
            }
        }
        return categoryNameMap;
    }

    private Map<String, Object> buildShoppingMomentum(Object rawBehaviorStats) {
        Map<String, Object> momentum = new LinkedHashMap<>();
        long totalEvents = 0L;
        if (rawBehaviorStats instanceof Collection<?>) {
            for (Object item : (Collection<?>) rawBehaviorStats) {
                if (!(item instanceof Map<?, ?>)) {
                    continue;
                }
                Object count = ((Map<?, ?>) item).get("count");
                totalEvents += readLong(count);
            }
        }

        String level;
        String summary;
        if (totalEvents >= 40) {
            level = "high";
            summary = "近期互动很密集，适合直接承接强意图推荐和对比决策。";
        } else if (totalEvents >= 12) {
            level = "medium";
            summary = "近期已有连续互动，推荐可以兼顾偏好匹配和热门候选补充。";
        } else if (totalEvents > 0) {
            level = "warming";
            summary = "行为还在积累阶段，推荐会更强调品类线索和热门候选补充。";
        } else {
            level = "cold";
            summary = "当前行为较少，更适合先用热门和类目偏好完成冷启动。";
        }

        momentum.put("level", level);
        momentum.put("eventCount", totalEvents);
        momentum.put("summary", summary);
        return momentum;
    }

    private Map<String, Object> buildJourneyStage(Map<String, Double> categoryWeights,
                                                  Set<String> userTags,
                                                  int interactedProducts) {
        Map<String, Object> stage = new LinkedHashMap<>();
        String code;
        String label;
        String summary;
        if (interactedProducts >= 12 || (categoryWeights != null && categoryWeights.size() >= 3)) {
            code = "preference_rich";
            label = "偏好明确";
            summary = "已经沉淀出稳定的品类和标签偏好，可以展示更强的个性化策略。";
        } else if (interactedProducts >= 4 || (userTags != null && userTags.size() >= 2)) {
            code = "warming_up";
            label = "正在升温";
            summary = "正在形成稳定兴趣，适合通过多路召回继续放大购买信号。";
        } else {
            code = "cold_start";
            label = "冷启动";
            summary = "当前画像较轻，优先结合热门结果和少量兴趣信号做稳妥承接。";
        }
        stage.put("code", code);
        stage.put("label", label);
        stage.put("summary", summary);
        return stage;
    }

    private Map<String, Object> buildRealtimeExecutiveSummary(Map<String, Object> profilePayload,
                                                              Map<String, Object> segmentPayload,
                                                              Map<String, Object> recommendationPayload,
                                                              Map<String, Object> dataSourcePayload) {
        Map<String, Object> summary = new LinkedHashMap<>();
        String segmentName = stringValue(segmentPayload == null ? null : segmentPayload.get("segmentName"));
        String journeyLabel = stringValue(profilePayload == null ? null : ((Map<?, ?>) profilePayload.get("journeyStage")) instanceof Map
                ? ((Map<?, ?>) profilePayload.get("journeyStage")).get("label")
                : null);
        String recommendationSource = stringValue(dataSourcePayload == null ? null : dataSourcePayload.get("recommendationSource"));
        String headline = firstNonEmpty(
                joinNonEmpty("，",
                        StringUtils.hasText(segmentName) ? "当前用户属于「" + segmentName + "」" : null,
                        StringUtils.hasText(journeyLabel) ? "画像阶段为" + journeyLabel : null,
                        StringUtils.hasText(recommendationSource) ? "推荐结果来自" + recommendationSource : null),
                "当前推荐已联动实时画像、最新分群和推荐结果。");

        summary.put("headline", headline);
        summary.put("profileSummary", stringValue(profilePayload == null ? null : profilePayload.get("profileSummary")));
        summary.put("segmentSummary", firstNonEmpty(
                stringValue(segmentPayload == null ? null : segmentPayload.get("personaSummary")),
                stringValue(segmentPayload == null ? null : segmentPayload.get("llmSummary")),
                stringValue(segmentPayload == null ? null : segmentPayload.get("message"))));
        summary.put("recommendationSummary", recommendationPayload == null
                ? null
                : recommendationPayload.get("recommendationSummary"));
        summary.put("refreshRule", stringValue(dataSourcePayload == null ? null : dataSourcePayload.get("refreshRule")));
        return summary;
    }

    private void collectTags(Object rawTags, Set<String> target) {
        if (rawTags == null || target == null) {
            return;
        }
        String text = String.valueOf(rawTags)
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "");
        for (String tag : text.split(",")) {
            String trimmed = tag == null ? "" : tag.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    private String buildProfileSummary(Map<String, Double> categoryWeights, Set<String> userTags) {
        String preferredCategory = categoryWeights == null || categoryWeights.isEmpty()
                ? "暂无显著类目偏好"
                : categoryWeights.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("暂无显著类目偏好");
        String preferredTag = userTags == null || userTags.isEmpty()
                ? "标签仍在积累"
                : userTags.iterator().next();
        return String.format("当前更偏好 %s，标签侧重点为 %s。", preferredCategory, preferredTag);
    }

    private List<String> buildFeatureHighlights(Map<String, Object> detail) {
        List<String> highlights = new ArrayList<>();
        highlights.add(String.format("90天订单 %d 单", readLong(detail.get("orderCount90d"))));
        highlights.add(String.format("30天行为 %d 次", readLong(detail.get("behaviorCount30d"))));
        highlights.add(String.format("30天活跃 %d 天", readLong(detail.get("activeDays30d"))));
        highlights.add(String.format("客单价 %.0f 元", parseDouble(detail.get("avgOrderAmount90d"))));
        long recencyDays = readLong(detail.get("recencyBehaviorDays"));
        highlights.add(recencyDays >= 9999 ? "近期行为较少" : String.format("最近行为距今 %d 天", recencyDays));
        return highlights;
    }

    private String resolveSegmentName(Map<String, Object> detail,
                                      AnalyticsKmeansSegment segment,
                                      ClusterContext clusterContext) {
        return firstNonEmpty(
                stringValue(detail.get("segmentName")),
                segment == null ? null : segment.getSegmentName(),
                clusterContext == null ? null : clusterContext.segmentName,
                "未命名分群");
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
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

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String joinNonEmpty(String delimiter, String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        String actualDelimiter = delimiter == null ? "" : delimiter;
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(actualDelimiter, parts);
    }

    private List<Product> getPersonalRecommendationsLive(Long userId, ClusterContext clusterContext, int limit) {
        return recommendationRecallService.getPersonalRecommendationsLive(
                userId,
                toRankingContext(clusterContext),
                limit,
                recallPort(clusterContext));
    }

    private List<Product> getSimilarProductsLive(Long productId, int limit) {
        return recommendationRecallService.getSimilarProductsLive(productId, limit, recallPort(null));
    }

    private List<Product> getHotRecommendationsLive(Long userId, ClusterContext clusterContext, int limit) {
        return recommendationRecallService.getHotRecommendationsLive(
                userId,
                toRankingContext(clusterContext),
                limit,
                recallPort(clusterContext));
    }

    private List<Product> getGuessYouLikeLive(Long userId, ClusterContext clusterContext, int limit) {
        return recommendationRecallService.getGuessYouLikeLive(
                userId,
                toRankingContext(clusterContext),
                limit,
                recallPort(clusterContext));
    }

    private RecommendationRecallService.RecallPort recallPort(ClusterContext clusterContext) {
        return new RecommendationRecallService.RecallPort() {
            @Override
            public List<Long> recommend(Long userId, int limit) {
                return hybridEngine.recommend(userId, limit);
            }

            @Override
            public List<Long> findSimilar(Long productId, int limit) {
                return hybridEngine.findSimilar(productId, limit);
            }

            @Override
            public List<Product> mapAvailableProductsByIds(List<Long> productIds, int limit) {
                return RecommendationServiceImpl.this.mapAvailableProductsByIds(productIds, limit);
            }

            @Override
            public List<Product> getClusterAwareFallback(RecommendationRerankService.RankingContext rankingContext, int limit) {
                return RecommendationServiceImpl.this.getClusterAwareFallback(clusterContext, limit);
            }

            @Override
            public List<Product> getDiverseRecommendations(int limit) {
                return RecommendationServiceImpl.this.getDiverseRecommendations(limit);
            }

            @Override
            public List<Product> loadRealtimeHotProducts(int limit) {
                return RecommendationServiceImpl.this.loadRealtimeHotProducts(limit);
            }

            @Override
            public List<Product> selectSimilarByCategory(Long productId, int limit) {
                Product sourceProduct = productMapper.selectById(productId);
                if (sourceProduct == null) {
                    return Collections.emptyList();
                }
                LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Product::getCategoryId, sourceProduct.getCategoryId());
                wrapper.ne(Product::getId, productId);
                wrapper.eq(Product::getStatus, Constants.ProductStatus.ON_SHELF);
                wrapper.orderByDesc(Product::getSalesCount);
                return normalizeRecommendationProducts(productMapper.selectPage(new Page<>(1, limit), wrapper).getRecords(), limit);
            }

            @Override
            public List<Product> rankByClusterContext(List<Product> products,
                                                      RecommendationRerankService.RankingContext rankingContext,
                                                      int limit) {
                return recommendationRerankService.rankByClusterContext(products, rankingContext, limit);
            }

            @Override
            public List<Product> boostByRecentPurchaseCategories(Long userId, List<Product> products) {
                return RecommendationServiceImpl.this.boostByRecentPurchaseCategories(userId, products);
            }

            @Override
            public List<Product> ensureCategoryDiversity(List<Product> products, int limit) {
                return recommendationRerankService.ensureCategoryDiversity(products, limit);
            }
        };
    }

    private LiveRecommendationDecision buildPersonalLiveDecision(Long userId,
                                                                 ClusterContext clusterContext,
                                                                 int limit) {
        int safeLimit = normalizeLimit(limit);
        List<Product> candidates = getPersonalRecommendationsLive(userId, clusterContext, safeLimit);
        List<Product> ranked = applyFeedDiversityByScene(candidates, safeLimit, SCENE_PERSONAL);
        if (ranked.size() < safeLimit) {
            ranked = appendFallbackProducts(
                    ranked,
                    getClusterAwareFallback(clusterContext, safeLimit),
                    safeLimit);
        }
        String algorithmTag = resolveLiveAlgorithmTag(userId);
        return new LiveRecommendationDecision(ranked, algorithmTag);
    }

    private LiveRecommendationDecision buildGuessYouLikeLiveDecision(Long userId,
                                                                     ClusterContext clusterContext,
                                                                     int limit) {
        int safeLimit = normalizeLimit(limit);
        List<Product> candidates = getGuessYouLikeLive(userId, clusterContext, safeLimit);
        List<Product> ranked = applyFeedDiversityByScene(candidates, safeLimit, SCENE_GUESS_YOU_LIKE);
        if (ranked.size() < safeLimit) {
            ranked = appendFallbackProducts(
                    ranked,
                    getHotRecommendationsLive(userId, clusterContext, safeLimit),
                    safeLimit);
        }
        String algorithmTag = resolveLiveAlgorithmTag(userId);
        return new LiveRecommendationDecision(ranked, algorithmTag);
    }

    private String resolveLiveAlgorithmTag(Long userId) {
        if (userId == null || userId <= 0) {
            return ALGO_HYBRID_LIVE_NO_CF;
        }
        try {
            Map<Long, Double> userVector = collaborativeFiltering.buildUserVector(userId);
            return userVector == null || userVector.isEmpty()
                    ? ALGO_HYBRID_LIVE_NO_CF
                    : ALGO_HYBRID_LIVE_CF;
        } catch (Exception exception) {
            log.debug("[Recommendation] Failed to resolve live algorithm tag for user {}: {}",
                    userId, exception.getMessage());
            return ALGO_HYBRID_LIVE_NO_CF;
        }
    }

    private boolean shouldPreferLiveRecommendations(Long userId) {
        if (userId == null) {
            return false;
        }
        if (hasRealtimePreferenceSignal(userId)) {
            return true;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(SNAPSHOT_BYPASS_PURCHASE_HOURS);
        UserBehavior latestPurchase = firstOrNull(behaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .select(UserBehavior::getId, UserBehavior::getCreateTime)
                        .eq(UserBehavior::getUserId, userId)
                        .eq(UserBehavior::getBehaviorType, Constants.BehaviorType.PURCHASE)
                        .ge(UserBehavior::getCreateTime, cutoff)
                        .orderByDesc(UserBehavior::getCreateTime)
                        .last("LIMIT 1")));
        return latestPurchase != null;
    }

    private boolean hasRealtimePreferenceSignal(Long userId) {
        return recommendationRealtimeCacheService.hasUserPreferenceSignal(userId);
    }

    private boolean hasRealtimeHotSignal() {
        return recommendationRealtimeCacheService.hasHotSignal();
    }

    private List<Product> loadRealtimeHotProducts(int limit) {
        int safeLimit = Math.max(1, limit);
        List<StreamProductHotnessRealtime> rows = recommendationRealtimeCacheService.getHotRows(safeLimit);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> productIds = new ArrayList<>();
        for (StreamProductHotnessRealtime row : rows) {
            if (row != null && row.getProductId() != null) {
                productIds.add(row.getProductId());
            }
        }
        return mapAvailableProductsByIds(productIds, safeLimit);
    }

    private List<Map<String, Object>> loadRealtimePreferenceRows(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        List<StreamUserCategoryPreference> rows = recommendationRealtimeCacheService.getUserPreferenceRows(userId, 10);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (StreamUserCategoryPreference row : rows) {
            if (row == null || row.getCategoryId() == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category_id", row.getCategoryId());
            item.put("category_name", row.getCategoryName());
            item.put("weight", row.getPreferenceScore());
            item.put("preferenceScore", row.getPreferenceScore());
            item.put("behavior_count", row.getBehaviorCount());
            item.put("last_event_time", row.getLastEventTime());
            result.add(item);
        }
        return result;
    }

    private List<Product> boostByRecentPurchaseCategories(Long userId, List<Product> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }
        Set<Long> preferredCategories = loadRecentPurchaseCategories(userId);
        if (preferredCategories.isEmpty()) {
            return candidates;
        }

        List<Product> preferred = new ArrayList<>();
        List<Product> others = new ArrayList<>();
        Set<Long> existingIds = new HashSet<>();
        for (Product product : candidates) {
            existingIds.add(product.getId());
            Long categoryId = product.getCategoryId();
            if (categoryId != null && preferredCategories.contains(categoryId)) {
                preferred.add(product);
            } else {
                others.add(product);
            }
        }

        int minPreferred = Math.max(4, candidates.size() / 4);
        if (preferred.size() < minPreferred) {
            for (Long categoryId : preferredCategories) {
                if (preferred.size() >= minPreferred) break;
                List<Product> extra = productMapper.selectRandomByCategory(categoryId, minPreferred);
                for (Product p : extra) {
                    if (p != null && p.getId() != null && existingIds.add(p.getId())) {
                        preferred.add(p);
                    }
                }
            }
        }

        List<Product> boosted = new ArrayList<>(preferred.size() + others.size());
        boosted.addAll(preferred);
        boosted.addAll(others);
        return boosted;
    }

    private Set<Long> loadRecentPurchaseCategories(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(RECENT_PURCHASE_CATEGORY_LOOKBACK_DAYS);
        List<UserBehavior> behaviors = behaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .select(UserBehavior::getProductId, UserBehavior::getBehaviorType, UserBehavior::getCreateTime)
                        .eq(UserBehavior::getUserId, userId)
                        .isNotNull(UserBehavior::getProductId)
                        .ge(UserBehavior::getCreateTime, cutoff)
                        .orderByDesc(UserBehavior::getCreateTime)
                        .last("LIMIT " + RECENT_PURCHASE_EVENT_LIMIT * 3));
        if (behaviors.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> productIds = behaviors.stream()
                .map(UserBehavior::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (productIds.isEmpty()) {
            return Collections.emptySet();
        }

        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productMapper.selectBatchIds(new ArrayList<>(productIds))) {
            productMap.put(product.getId(), product);
        }

        Map<Long, Double> categoryScore = new LinkedHashMap<>();
        for (UserBehavior behavior : behaviors) {
            Product product = productMap.get(behavior.getProductId());
            if (product == null || product.getCategoryId() == null) {
                continue;
            }
            double weight;
            String type = behavior.getBehaviorType();
            if (Constants.BehaviorType.PURCHASE.equals(type)) {
                weight = BEHAVIOR_WEIGHT_PURCHASE;
            } else if (Constants.BehaviorType.FAVORITE.equals(type)) {
                weight = BEHAVIOR_WEIGHT_FAVORITE;
            } else if (Constants.BehaviorType.CART.equals(type)) {
                weight = BEHAVIOR_WEIGHT_CART;
            } else if (Constants.BehaviorType.VIEW.equals(type)) {
                weight = BEHAVIOR_WEIGHT_VIEW;
            } else {
                continue;
            }
            categoryScore.merge(product.getCategoryId(), weight, Double::sum);
        }

        return categoryScore.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(RECENT_PURCHASE_CATEGORY_LIMIT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Product> applyDislikeSuppression(Long userId, List<Product> candidates, int requestedLimit) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        if (userId == null || userId <= 0) {
            return candidates;
        }

        int safeLookbackDays = Math.max(1, dislikeLookbackDays);
        int safeMaxHistory = Math.max(20, dislikeMaxHistory);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeLookbackDays);

        List<UserBehavior> dislikeBehaviors = behaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .select(UserBehavior::getProductId, UserBehavior::getSearchKeyword, UserBehavior::getCreateTime)
                        .eq(UserBehavior::getUserId, userId)
                        .eq(UserBehavior::getBehaviorType, Constants.BehaviorType.DISLIKE)
                        .isNotNull(UserBehavior::getProductId)
                        .ge(UserBehavior::getCreateTime, cutoff)
                        .orderByDesc(UserBehavior::getCreateTime)
                        .last("LIMIT " + safeMaxHistory));
        if (dislikeBehaviors == null || dislikeBehaviors.isEmpty()) {
            return candidates;
        }

        Set<Long> dislikedProductIds = dislikeBehaviors.stream()
                .map(UserBehavior::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (dislikedProductIds.isEmpty()) {
            return candidates;
        }

        Map<Long, Integer> dislikeCategoryCounter = new HashMap<>();
        Set<Long> strictCategoryIds = new LinkedHashSet<>();
        Map<String, Integer> priceBandCounter = new HashMap<>();
        List<Product> dislikedProducts = productMapper.selectBatchIds(new ArrayList<>(dislikedProductIds));
        Map<Long, Product> dislikedProductMap = new HashMap<>();
        if (dislikedProducts != null && !dislikedProducts.isEmpty()) {
            for (Product dislikedProduct : dislikedProducts) {
                if (dislikedProduct != null && dislikedProduct.getId() != null) {
                    dislikedProductMap.put(dislikedProduct.getId(), dislikedProduct);
                }
                if (dislikedProduct == null || dislikedProduct.getCategoryId() == null) {
                    continue;
                }
                dislikeCategoryCounter.merge(dislikedProduct.getCategoryId(), 1, Integer::sum);
            }
        }
        for (UserBehavior row : dislikeBehaviors) {
            if (row == null || row.getProductId() == null) {
                continue;
            }
            String reason = RecommendationNegativeFeedbackPolicy.normalizeDislikeReason(row.getSearchKeyword());
            Product dislikedProduct = dislikedProductMap.get(row.getProductId());
            if ("category_dislike".equals(reason) && dislikedProduct != null && dislikedProduct.getCategoryId() != null) {
                strictCategoryIds.add(dislikedProduct.getCategoryId());
            }
            if ("price_high".equals(reason) && dislikedProduct != null) {
                String band = RecommendationNegativeFeedbackPolicy.priceBand(dislikedProduct.getPrice());
                if (StringUtils.hasText(band)) {
                    priceBandCounter.merge(band, 1, Integer::sum);
                }
            }
        }
        int categorySuppressThreshold = Math.max(1, dislikeCategorySuppressThreshold);
        Set<Long> suppressedCategoryIds = dislikeCategoryCounter.entrySet().stream()
                .filter(entry -> entry.getValue() >= categorySuppressThreshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        suppressedCategoryIds.addAll(strictCategoryIds);
        Set<String> suppressedPriceBands = priceBandCounter.entrySet().stream()
                .filter(entry -> entry.getValue() >= 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int safeRequestedLimit = Math.max(1, requestedLimit);
        int minRemaining = Math.max(1, Math.min(safeRequestedLimit, Math.max(dislikeMinRemaining, safeRequestedLimit / 2)));
        List<Product> filtered = new ArrayList<>();
        Set<Long> selectedIds = new LinkedHashSet<>();

        for (Product product : candidates) {
            if (product == null || product.getId() == null) {
                continue;
            }
            Long productId = product.getId();
            if (selectedIds.contains(productId)) {
                continue;
            }
            if (dislikedProductIds.contains(productId)) {
                continue;
            }
            if (product.getCategoryId() != null && suppressedCategoryIds.contains(product.getCategoryId())) {
                continue;
            }
            if (!suppressedPriceBands.isEmpty()
                    && suppressedPriceBands.contains(RecommendationNegativeFeedbackPolicy.priceBand(product.getPrice()))) {
                continue;
            }
            selectedIds.add(productId);
            filtered.add(product);
            if (filtered.size() >= safeRequestedLimit) {
                break;
            }
        }

        if (filtered.size() < minRemaining) {
            for (Product product : candidates) {
                if (product == null || product.getId() == null) {
                    continue;
                }
                Long productId = product.getId();
                if (dislikedProductIds.contains(productId)) {
                    continue;
                }
                if (selectedIds.add(productId)) {
                    filtered.add(product);
                    if (filtered.size() >= safeRequestedLimit) {
                        break;
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            return candidates;
        }
        return filtered;
    }

    @Override
    public void recordRecommendationEvent(Long userId, RecommendationEventDTO eventDTO) {
        if (userId == null || userId <= 0 || eventDTO == null) {
            return;
        }

        String eventType = normalizeRecommendationEventType(eventDTO.getEventType());
        if (!StringUtils.hasText(eventType)) {
            return;
        }
        if (!claimRecommendationEventIdempotency(userId, eventDTO, eventType)) {
            log.debug("[RecommendationEvent] duplicate ignored userId={} eventType={} productId={} token={}",
                    userId, eventType, eventDTO.getProductId(), eventDTO.getRecommendationToken());
            return;
        }

        LocalDateTime eventTime = eventDTO.getEventTime() == null ? LocalDateTime.now() : eventDTO.getEventTime();
        RecommendationEvent event = new RecommendationEvent();
        event.setUserId(userId);
        event.setProductId(eventDTO.getProductId());
        event.setEventType(eventType);
        event.setScene(eventDTO.getScene());
        event.setTraceId(eventDTO.getTraceId());
        event.setRecommendationToken(eventDTO.getRecommendationToken());
        event.setExperimentGroup(eventDTO.getExperimentGroup());
        event.setDuration(eventDTO.getDuration());
        event.setOrderId(eventDTO.getOrderId());
        event.setAmount(eventDTO.getAmount());
        event.setEventTime(eventTime);
        event.setMetadata(eventDTO.getMetadata());
        recommendationEventMapper.insert(event);
        if (Constants.RecommendationEventType.EXPOSURE.equals(eventType)) {
            recommendationAttributionService.persistExposureFromRecommendationEvent(userId, eventDTO, eventTime);
        } else if ((Constants.RecommendationEventType.ORDER.equals(eventType)
                || Constants.RecommendationEventType.REFUND.equals(eventType))
                && eventDTO.getOrderId() != null
                && eventDTO.getProductId() == null) {
            recommendationAttributionService.attributeOrderEventToRecentExposures(userId, eventDTO, eventType, eventTime);
        }
        streamRealtimeProjectionService.projectRecommendationEvent(userId, eventDTO, eventType);
        recommendationRealtimeCacheService.invalidateUser(userId);

        String behaviorType = toBehaviorType(eventType);
        if (!StringUtils.hasText(behaviorType)) {
            return;
        }
        if (Constants.BehaviorType.PURCHASE.equals(behaviorType)
                && !StringUtils.hasText(eventDTO.getRecommendationToken())) {
            return;
        }
        if ((Constants.BehaviorType.VIEW.equals(behaviorType)
                || Constants.BehaviorType.CART.equals(behaviorType)
                || Constants.BehaviorType.PURCHASE.equals(behaviorType))
                && eventDTO.getProductId() == null) {
            return;
        }

        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setProductId(eventDTO.getProductId());
        behavior.setBehaviorType(behaviorType);
        behavior.setDuration(eventDTO.getDuration());
        behavior.setCreateTime(eventTime);
        behavior.setRecommendationToken(eventDTO.getRecommendationToken());
        behavior.setRecommendationScene(eventDTO.getScene());
        behavior.setOrderId(eventDTO.getOrderId());
        recordBehaviorInternal(behavior, false);
    }

    @Override
    public void recordBehavior(UserBehavior behavior) {
        recordBehaviorInternal(behavior, true);
    }

    private void recordBehaviorInternal(UserBehavior behavior, boolean projectRealtime) {
        if (behavior == null) {
            return;
        }
        behaviorMapper.insert(behavior);
        if (projectRealtime) {
            streamRealtimeProjectionService.projectBehavior(behavior);
        }
        recommendationRealtimeCacheService.invalidateUser(behavior.getUserId());

        if (!StringUtils.hasText(behavior.getRecommendationToken())
                || behavior.getUserId() == null
                || behavior.getProductId() == null) {
            return;
        }

        AnalyticsRecommendationExposure exposure = analyticsRecommendationExposureMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsRecommendationExposure>()
                        .eq(AnalyticsRecommendationExposure::getExposureToken, behavior.getRecommendationToken())
                        .eq(AnalyticsRecommendationExposure::getUserId, behavior.getUserId())
                        .eq(AnalyticsRecommendationExposure::getProductId, behavior.getProductId())
                        .last("LIMIT 1"));
        if (exposure == null) {
            return;
        }

        LocalDateTime eventTime = behavior.getCreateTime() != null ? behavior.getCreateTime() : LocalDateTime.now();
        boolean exposureChanged = false;
        boolean clickRecorded = false;
        boolean cartRecorded = false;
        boolean purchaseRecorded = false;

        if (Constants.BehaviorType.VIEW.equals(behavior.getBehaviorType())
                || Constants.BehaviorType.FAVORITE.equals(behavior.getBehaviorType())
                || Constants.BehaviorType.CART.equals(behavior.getBehaviorType())) {
            if (exposure.getClickTime() == null) {
                exposure.setClickTime(eventTime);
                exposureChanged = true;
                clickRecorded = true;
            }
        }
        if (Constants.BehaviorType.FAVORITE.equals(behavior.getBehaviorType())
                && exposure.getFavoriteTime() == null) {
            exposure.setFavoriteTime(eventTime);
            exposureChanged = true;
        }
        if (Constants.BehaviorType.CART.equals(behavior.getBehaviorType())
                && exposure.getCartTime() == null) {
            exposure.setCartTime(eventTime);
            exposureChanged = true;
            cartRecorded = true;
        }
        if (Constants.BehaviorType.PURCHASE.equals(behavior.getBehaviorType())
                && exposure.getPurchaseTime() == null) {
            exposure.setPurchaseTime(eventTime);
            if (behavior.getOrderId() != null) {
                exposure.setOrderId(behavior.getOrderId());
            }
            exposureChanged = true;
            purchaseRecorded = true;
        }

        if (exposureChanged) {
            analyticsRecommendationExposureMapper.updateById(exposure);
        }

        String experimentGroup = exposure.getExperimentGroup();
        if (clickRecorded && StringUtils.hasText(experimentGroup) && !"disabled".equalsIgnoreCase(experimentGroup)) {
            abTestFramework.recordClick(behavior.getUserId(), experimentGroup, behavior.getProductId());
        }
        if (cartRecorded && StringUtils.hasText(experimentGroup) && !"disabled".equalsIgnoreCase(experimentGroup)) {
            abTestFramework.recordAddToCart(behavior.getUserId(), experimentGroup, behavior.getProductId());
        }
        if (purchaseRecorded && StringUtils.hasText(experimentGroup) && !"disabled".equalsIgnoreCase(experimentGroup)) {
            abTestFramework.recordPurchase(behavior.getUserId(), experimentGroup, behavior.getProductId());
        }
    }

    private boolean claimRecommendationEventIdempotency(Long userId,
                                                        RecommendationEventDTO eventDTO,
                                                        String eventType) {
        String key = buildRecommendationEventIdempotencyKey(userId, eventDTO, eventType);
        if (!StringUtils.hasText(key)) {
            return true;
        }
        long ttl = Math.max(60L, Math.min(recommendationEventIdempotencyTtlSeconds, 7 * 24 * 3600L));
        try {
            return Boolean.TRUE.equals(redisUtil.setIfAbsent(key, "1", ttl, TimeUnit.SECONDS));
        } catch (Exception exception) {
            log.debug("[RecommendationEvent] idempotency fallback allow key={} reason={}", key, exception.getMessage());
            return true;
        }
    }

    private String buildRecommendationEventIdempotencyKey(Long userId,
                                                          RecommendationEventDTO eventDTO,
                                                          String eventType) {
        if (eventDTO == null || userId == null) {
            return null;
        }
        String traceId = stringValue(eventDTO.getTraceId());
        String token = stringValue(eventDTO.getRecommendationToken());
        String scene = stringValue(eventDTO.getScene());
        Long productId = eventDTO.getProductId();
        String stablePart = StringUtils.hasText(traceId)
                ? traceId
                : (StringUtils.hasText(token) ? token : scene + ":" + productId + ":" + eventDTO.getOrderId());
        if (!StringUtils.hasText(stablePart)) {
            return null;
        }
        return "recommend:event:idempotent:" + userId + ":" + eventType + ":" + productId + ":" + stablePart;
    }

    @Override
    public Map<String, Object> getRecommendationMetrics(int days) {
        int safeDays = days <= 0 ? DEFAULT_METRIC_DAYS : Math.min(days, MAX_METRIC_DAYS);
        LocalDate startDate = LocalDate.now().minusDays(Math.max(0, safeDays - 1));
        LocalDateTime startTime = startDate.atStartOfDay();

        Map<String, Object> overall = buildMetricBlock(analyticsRecommendationExposureMapper.selectOverallMetrics(startTime));
        List<Map<String, Object>> sceneMetrics = buildMetricList(analyticsRecommendationExposureMapper.selectSceneMetrics(startTime));
        List<Map<String, Object>> segmentMetrics = buildMetricList(analyticsRecommendationExposureMapper.selectSegmentMetrics(startTime));
        List<Map<String, Object>> algorithmMetrics = buildMetricList(analyticsRecommendationExposureMapper.selectAlgorithmMetrics(startTime));
        List<Map<String, Object>> algorithmSegmentMetrics = buildMetricList(analyticsRecommendationExposureMapper.selectAlgorithmSegmentMetrics(startTime));
        List<Map<String, Object>> sceneAlgorithmMetrics = loadOptionalMetricList("sceneAlgorithmMetrics",
                () -> analyticsRecommendationExposureMapper.selectSceneAlgorithmMetrics(startTime));
        List<Map<String, Object>> sourceTypeMetrics = loadOptionalMetricList("sourceTypeMetrics",
                () -> analyticsRecommendationExposureMapper.selectSourceTypeMetrics(startTime));
        List<Map<String, Object>> reasonTypeMetrics = loadOptionalMetricList("reasonTypeMetrics",
                () -> analyticsRecommendationExposureMapper.selectReasonTypeMetrics(startTime));
        List<Map<String, Object>> modelVersionMetrics = loadOptionalMetricList("modelVersionMetrics",
                () -> analyticsRecommendationExposureMapper.selectModelVersionMetrics(startTime));
        List<Map<String, Object>> dailyTrend = buildMetricList(analyticsRecommendationExposureMapper.selectDailyMetrics(startTime));

        Map<String, Object> sevenDayRepurchaseRaw = analyticsRecommendationExposureMapper.selectSevenDayRepurchaseMetrics(startTime);
        long attributedPurchaseUsers = readLong(sevenDayRepurchaseRaw == null ? null : sevenDayRepurchaseRaw.get("attributedPurchaseUsers"));
        long repurchaseUsers = readLong(sevenDayRepurchaseRaw == null ? null : sevenDayRepurchaseRaw.get("repurchaseUsers"));

        Map<String, Object> sevenDayRepurchase = new LinkedHashMap<>();
        sevenDayRepurchase.put("attributedPurchaseUsers", attributedPurchaseUsers);
        sevenDayRepurchase.put("repurchaseUsers", repurchaseUsers);
        sevenDayRepurchase.put("sevenDayRepurchaseRate", ratioPercent(repurchaseUsers, attributedPurchaseUsers));

        overall.put("attributedPurchaseUsers", attributedPurchaseUsers);
        overall.put("repurchaseUsers", repurchaseUsers);
        overall.put("sevenDayRepurchaseRate", sevenDayRepurchase.get("sevenDayRepurchaseRate"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", safeDays);
        result.put("startDate", startDate);
        result.put("endDate", LocalDate.now());
        result.put("summary", overall);
        result.put("sceneMetrics", sceneMetrics);
        result.put("segmentMetrics", segmentMetrics);
        result.put("algorithmMetrics", algorithmMetrics);
        result.put("algorithmSegmentMetrics", algorithmSegmentMetrics);
        result.put("sceneAlgorithmMetrics", sceneAlgorithmMetrics);
        result.put("sourceTypeMetrics", sourceTypeMetrics);
        result.put("reasonTypeMetrics", reasonTypeMetrics);
        result.put("modelVersionMetrics", modelVersionMetrics);
        result.put("sevenDayRepurchase", sevenDayRepurchase);
        result.put("dailyTrend", dailyTrend);
        Map<String, Object> distributionQuality = buildDistributionQuality(
                overall, sceneMetrics, algorithmMetrics, segmentMetrics, reasonTypeMetrics);
        List<Map<String, Object>> comparisonHighlights = buildComparisonHighlights(
                sceneMetrics, segmentMetrics, algorithmMetrics);
        Map<String, Object> bestAlgorithmSegment = buildBestAlgorithmSegment(algorithmSegmentMetrics);
        result.put("distributionQuality", distributionQuality);
        result.put("comparisonHighlights", comparisonHighlights);
        result.put("bestAlgorithmSegment", bestAlgorithmSegment);
        result.put("sceneAlgorithmLeaders", buildSceneAlgorithmLeaders(sceneAlgorithmMetrics));
        result.put("optimizationStages", buildOptimizationStages(
                overall,
                sourceTypeMetrics,
                reasonTypeMetrics,
                algorithmMetrics,
                distributionQuality,
                sevenDayRepurchase));
        result.put("attributionHealth", recommendationAttributionService.buildAttributionHealth(startTime));
        result.put("cacheStats", recommendationRealtimeCacheService.getCacheStatsSnapshot());
        result.put("diagnosticCards", buildDiagnosticCards(
                overall,
                distributionQuality,
                sevenDayRepurchase,
                comparisonHighlights,
                bestAlgorithmSegment));
        result.put("defenseNarrative", buildDefenseNarrative(
                overall,
                distributionQuality,
                sevenDayRepurchase,
                comparisonHighlights,
                bestAlgorithmSegment));

        Map<String, Object> formula = new LinkedHashMap<>();
        formula.put("conversionRate", "purchaseCount / exposureCount * 100");
        formula.put("recommendationSuccessRate", "successCount / exposureCount * 100");
        formula.put("clickThroughRate", "clickCount / exposureCount * 100");
        formula.put("addToCartRate", "cartCount / exposureCount * 100");
        formula.put("postClickConversionRate", "purchaseCount / clickCount * 100");
        formula.put("sevenDayRepurchaseRate", "repurchaseUsers / attributedPurchaseUsers * 100");
        formula.put("successDefinition", "A recommendation is successful when it triggers at least one downstream action: click, favorite, cart, or purchase.");
        formula.put("distributionQualityScore", "100 - ((sceneHhi * 0.4 + algorithmHhi * 0.4 + segmentHhi * 0.2) * 100)");
        result.put("formula", formula);
        return result;
    }

    @Override
    public List<Product> getProductsByIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<Long> orderedIds = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (productId != null) {
                orderedIds.add(productId);
            }
        }
        if (orderedIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Product> loadedProducts = normalizeRecommendationProducts(
                productService.getProductsByIds(new ArrayList<>(orderedIds)),
                orderedIds.size());
        if (loadedProducts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Product> productMap = loadedProducts.stream()
                .filter(product -> product != null && product.getId() != null)
                .collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<Product> orderedProducts = new ArrayList<>();
        for (Long productId : orderedIds) {
            Product product = productMap.get(productId);
            if (product != null) {
                orderedProducts.add(product);
            }
        }
        return orderedProducts;
    }

    @Override
    public List<UserBehavior> getUserBehaviors(Long userId, String type, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, userId);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(UserBehavior::getBehaviorType, type);
        }
        wrapper.orderByDesc(UserBehavior::getCreateTime);
        return behaviorMapper.selectPage(new Page<>(safePage, safeSize), wrapper).getRecords();
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private List<Product> loadRecommendationSnapshotProducts(String scene, Long userId, int limit) {
        List<AnalyticsRecommendationResult> rows = loadRecommendationSnapshotRows(scene, userId, limit);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return mapAvailableProductsByIds(extractProductIds(rows), limit);
    }

    private List<AnalyticsRecommendationResult> loadRecommendationSnapshotRows(String scene, Long userId, int limit) {
        Long snapshotUserId = SCENE_HOT.equals(scene) ? 0L : userId;
        if (snapshotUserId == null) {
            return Collections.emptyList();
        }

        try {
            AnalyticsRecommendationResult latest = firstOrNull(analyticsRecommendationResultMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsRecommendationResult>()
                            .eq(AnalyticsRecommendationResult::getScene, scene)
                            .eq(AnalyticsRecommendationResult::getUserId, snapshotUserId)
                            .orderByDesc(AnalyticsRecommendationResult::getSnapshotDate)
                            .orderByAsc(AnalyticsRecommendationResult::getRankNo)
                            .last("LIMIT 1")));
            if (latest == null || latest.getSnapshotDate() == null) {
                return Collections.emptyList();
            }

            return analyticsRecommendationResultMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsRecommendationResult>()
                            .eq(AnalyticsRecommendationResult::getScene, scene)
                            .eq(AnalyticsRecommendationResult::getUserId, snapshotUserId)
                            .eq(AnalyticsRecommendationResult::getSnapshotDate, latest.getSnapshotDate())
                            .orderByAsc(AnalyticsRecommendationResult::getRankNo)
                            .last("LIMIT " + getSnapshotFetchLimit(limit)));
        } catch (Exception exception) {
            log.warn("[Recommendation] Failed to load {} snapshot for user {}: {}",
                    scene, snapshotUserId, exception.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Product> loadSimilaritySnapshotProducts(Long productId, int limit) {
        try {
            AnalyticsProductSimilarity latest = firstOrNull(analyticsProductSimilarityMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsProductSimilarity>()
                            .eq(AnalyticsProductSimilarity::getProductId, productId)
                            .orderByDesc(AnalyticsProductSimilarity::getSnapshotDate)
                            .orderByAsc(AnalyticsProductSimilarity::getRankNo)
                            .last("LIMIT 1")));
            if (latest == null || latest.getSnapshotDate() == null) {
                return Collections.emptyList();
            }

            List<AnalyticsProductSimilarity> rows = analyticsProductSimilarityMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsProductSimilarity>()
                            .eq(AnalyticsProductSimilarity::getProductId, productId)
                            .eq(AnalyticsProductSimilarity::getSnapshotDate, latest.getSnapshotDate())
                            .orderByAsc(AnalyticsProductSimilarity::getRankNo)
                            .orderByDesc(AnalyticsProductSimilarity::getSimilarity)
                            .last("LIMIT " + getSnapshotFetchLimit(limit)));
            if (rows.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> productIds = new ArrayList<>();
            for (AnalyticsProductSimilarity row : rows) {
                if (row.getSimilarProductId() != null && !Objects.equals(row.getSimilarProductId(), productId)) {
                    productIds.add(row.getSimilarProductId());
                }
            }
            return mapAvailableProductsByIds(productIds, limit);
        } catch (Exception exception) {
            log.warn("[Recommendation] Failed to load similar-product snapshot for product {}: {}",
                    productId, exception.getMessage());
            return Collections.emptyList();
        }
    }

    private int getSnapshotFetchLimit(int limit) {
        int safeLimit = normalizeLimit(limit);
        return Math.max(safeLimit, safeLimit * SNAPSHOT_FETCH_MULTIPLIER);
    }

    private List<Long> extractProductIds(List<AnalyticsRecommendationResult> rows) {
        List<Long> productIds = new ArrayList<>();
        for (AnalyticsRecommendationResult row : rows) {
            if (row.getProductId() != null) {
                productIds.add(row.getProductId());
            }
        }
        return productIds;
    }

    private List<Long> extractLongIds(Object rawValue) {
        if (!(rawValue instanceof Collection<?>)) {
            return Collections.emptyList();
        }

        List<Long> result = new ArrayList<>();
        for (Object item : (Collection<?>) rawValue) {
            Long parsedValue = parseLong(item);
            if (parsedValue != null) {
                result.add(parsedValue);
            }
        }
        return result;
    }

    private List<Map<String, Object>> alignExplanations(Object rawValue, List<Long> orderedProductIds) {
        if (!(rawValue instanceof Collection<?>) || orderedProductIds == null || orderedProductIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Map<String, Object>> explanationMap = new HashMap<>();
        for (Object item : (Collection<?>) rawValue) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }

            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                if (entry.getKey() != null) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }

            Long productId = parseLong(normalized.get("productId"));
            if (productId != null) {
                explanationMap.putIfAbsent(productId, normalized);
            }
        }

        List<Map<String, Object>> orderedExplanations = new ArrayList<>();
        for (Long productId : orderedProductIds) {
            Map<String, Object> explanation = explanationMap.get(productId);
            if (explanation != null) {
                orderedExplanations.add(explanation);
            }
        }
        return orderedExplanations;
    }

    private List<Map<String, Object>> mergeProductReasonsIntoExplanations(List<Product> products,
                                                                          List<Map<String, Object>> explanations) {
        if (products == null || products.isEmpty()) {
            return explanations == null ? Collections.emptyList() : explanations;
        }
        Map<Long, Map<String, Object>> existing = new HashMap<>();
        if (explanations != null) {
            for (Map<String, Object> explanation : explanations) {
                if (explanation == null) {
                    continue;
                }
                Long productId = parseLong(explanation.get("productId"));
                if (productId != null) {
                    existing.put(productId, new LinkedHashMap<>(explanation));
                }
            }
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            Map<String, Object> explanation = existing.getOrDefault(product.getId(), new LinkedHashMap<>());
            explanation.put("productId", product.getId());
            if (StringUtils.hasText(product.getReasonType())) {
                explanation.put("primaryReason", product.getReasonType());
            }
            if (StringUtils.hasText(product.getRecommendReason())) {
                explanation.put("reasonText", product.getRecommendReason());
                explanation.put("reasons", List.of(product.getRecommendReason()));
            }
            merged.add(explanation);
        }
        return merged;
    }

    private void enrichProductsWithExplanationMetadata(List<Product> products,
                                                       List<Map<String, Object>> explanations,
                                                       String scene,
                                                       String sourceType,
                                                       String modelVersion,
                                                       LocalDate snapshotDateHint,
                                                       Long userId) {
        if (products == null || products.isEmpty()) {
            return;
        }

        Map<Long, Map<String, Object>> explanationMap = new HashMap<>();
        if (explanations != null) {
            for (Map<String, Object> explanation : explanations) {
                if (explanation == null) {
                    continue;
                }
                Long productId = parseLong(explanation.get("productId"));
                if (productId != null) {
                    explanationMap.put(productId, explanation);
                }
            }
        }

        Map<Long, StreamProductHotnessRealtime> hotnessByProduct = loadRealtimeHotnessMap(products);
        LocalDateTime latestPreferenceUpdateTime = recommendationRealtimeCacheService.getUserPreferenceLatestUpdateTime(userId);
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            Map<String, Object> explanation = explanationMap.get(product.getId());
            product.setSourceType(sourceType);
            product.setRecommendationSourceType(sourceType);
            product.setModelVersion(StringUtils.hasText(modelVersion) ? modelVersion : resolveProductModelVersion(null, null, sourceType));
            if (explanation != null) {
                product.setReasonType(firstNonEmpty(product.getReasonType(), stringValue(explanation.get("primaryReason"))));
                product.setRecommendReason(firstNonEmpty(product.getRecommendReason(), stringValue(explanation.get("reasonText"))));
            }
            if (!StringUtils.hasText(product.getReasonType())) {
                product.setReasonType(buildDefaultReasonTypeByScene(scene, sourceType));
            }
            if (!StringUtils.hasText(product.getRecommendReason())) {
                product.setRecommendReason(buildDefaultReasonByScene(scene, sourceType));
            }
            String freshness = resolveDataFreshness(sourceType, scene, null,
                    hotnessByProduct.get(product.getId()), latestPreferenceUpdateTime, snapshotDateHint);
            product.setDataFreshness(freshness);
        }
    }

    private Long parseLong(Object rawValue) {
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        if (rawValue instanceof String && StringUtils.hasText((String) rawValue)) {
            try {
                return Long.parseLong(((String) rawValue).trim());
            } catch (NumberFormatException e) {
                log.debug("[Recommendation] Long解析失败: {}", rawValue);
                return null;
            }
        }
        return null;
    }

    private List<Product> mapAvailableProductsByIds(List<Long> productIds, int limit) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<Long> orderedIds = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (productId != null) {
                orderedIds.add(productId);
            }
        }
        if (orderedIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Product> loadedProducts = normalizeRecommendationProducts(
                productService.getProductsByIds(new ArrayList<>(orderedIds)),
                Math.max(limit, orderedIds.size()));
        if (loadedProducts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Product> productMap = loadedProducts.stream()
                .filter(product -> product != null && product.getId() != null)
                .collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<Product> orderedProducts = new ArrayList<>();
        for (Long productId : orderedIds) {
            Product product = productMap.get(productId);
            if (product != null) {
                orderedProducts.add(product);
                if (orderedProducts.size() >= limit) {
                    break;
                }
            }
        }
        return orderedProducts;
    }

    private List<Product> normalizeRecommendationProducts(List<Product> products, int limit) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, limit);
        List<Product> shelfProducts = new ArrayList<>(products.size());
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            if (!Objects.equals(product.getStatus(), Constants.ProductStatus.ON_SHELF)) {
                continue;
            }
            shelfProducts.add(product);
        }
        List<Product> filtered = seckillService.excludeShelfSeckillProducts(shelfProducts);
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }
        List<Product> normalized = new ArrayList<>(Math.min(filtered.size(), safeLimit));
        Set<Long> seen = new LinkedHashSet<>();
        for (Product product : filtered) {
            if (product == null || product.getId() == null || !seen.add(product.getId())) {
                continue;
            }
            normalized.add(product);
            if (normalized.size() >= safeLimit) {
                break;
            }
        }
        return normalized;
    }

    private List<Product> appendFallbackProducts(List<Product> primary, List<Product> fallback, int limit) {
        List<Product> merged = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (Product product : primary) {
            if (product != null && product.getId() != null && seen.add(product.getId())) {
                merged.add(product);
                if (merged.size() >= limit) {
                    return merged;
                }
            }
        }
        for (Product product : fallback) {
            if (product != null && product.getId() != null && seen.add(product.getId())) {
                merged.add(product);
                if (merged.size() >= limit) {
                    break;
                }
            }
        }
        return merged;
    }

    private Map<String, Object> buildSnapshotExplanationResponse(
            List<Product> products,
            List<AnalyticsRecommendationResult> snapshotRows) {
        Map<Long, AnalyticsRecommendationResult> rowMap = new HashMap<>();
        for (AnalyticsRecommendationResult row : snapshotRows) {
            if (row.getProductId() != null) {
                rowMap.putIfAbsent(row.getProductId(), row);
            }
        }

        List<Long> productIds = new ArrayList<>();
        List<Map<String, Object>> explanations = new ArrayList<>();
        for (Product product : products) {
            productIds.add(product.getId());

            AnalyticsRecommendationResult row = rowMap.get(product.getId());
            Map<String, Object> explanation = new LinkedHashMap<>();
            String primaryReason = firstNonEmpty(
                    product.getReasonType(),
                    resolvePrimaryReason(row != null ? row.getAlgorithm() : null));
            explanation.put("productId", product.getId());
            explanation.put("primaryReason", primaryReason);

            List<String> reasons = new ArrayList<>();
            if (StringUtils.hasText(product.getRecommendReason())) {
                reasons.add(product.getRecommendReason());
            } else {
                reasons.add(buildSnapshotReason(row));
            }
            if (!StringUtils.hasText(product.getRecommendReason()) && row != null && row.getRankNo() != null && row.getRankNo() > 0) {
                reasons.add("推荐位次 #" + row.getRankNo());
            }
            String reasonText = String.join(" · ", reasons);
            explanation.put("reasons", reasons);
            explanation.put("reasonText", reasonText);
            explanations.add(explanation);

            product.setRecommendReason(reasonText);
            product.setReasonType(primaryReason);
            product.setSourceType(SOURCE_SNAPSHOT);
            product.setRecommendationSourceType(SOURCE_SNAPSHOT);
            product.setModelVersion(resolveProductModelVersion(row, null, SOURCE_SNAPSHOT));
            product.setDataFreshness(resolveDataFreshness(
                    SOURCE_SNAPSHOT, SCENE_PERSONAL, row, null, null, null));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productIds", productIds);
        result.put("products", products);
        result.put("explanations", explanations);

        Map<String, Object> algorithmWeights = new LinkedHashMap<>();
        algorithmWeights.put("snapshot", 1.0);
        result.put("algorithmWeights", algorithmWeights);
        result.put("experimentGroup", "snapshot");
        result.put("sourceType", SOURCE_SNAPSHOT);

        AnalyticsRecommendationResult firstRow = firstOrNull(snapshotRows);
        if (firstRow != null) {
            result.put("snapshotDate", firstRow.getSnapshotDate());
            result.put("modelVersion", firstRow.getModelVersion());
            result.put("dataFreshness", resolveDataFreshness(
                    SOURCE_SNAPSHOT, SCENE_PERSONAL, firstRow, null, null, firstRow.getSnapshotDate()));
        } else {
            result.put("dataFreshness", "离线快照");
        }
        return result;
    }

    private String resolvePrimaryReason(String algorithm) {
        if (!StringUtils.hasText(algorithm)) {
            return "COLD_START";
        }

        String normalized = algorithm.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("hot")) {
            return "HOT_SELLING";
        }
        if (normalized.contains("cf")) {
            return "COLLABORATIVE";
        }
        if (normalized.contains("content") || normalized.contains("cb")) {
            return "CONTENT_CATEGORY";
        }
        if (normalized.contains("similar") || normalized.contains("item")) {
            return "SIMILAR_PRODUCT";
        }
        return "COLD_START";
    }

    private String buildSnapshotReason(AnalyticsRecommendationResult row) {
        if (row == null) {
            return "结合你的历史行为与偏好为你精选";
        }
        if (StringUtils.hasText(row.getReason())) {
            return row.getReason().trim();
        }
        if (!StringUtils.hasText(row.getAlgorithm())) {
            return "结合你的历史行为与偏好为你精选";
        }

        String normalized = row.getAlgorithm().trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("hot")) {
            return "当前热度较高，近期转化表现稳定";
        }
        if (normalized.contains("cf")) {
            return "与你兴趣相近的用户近期也在关注";
        }
        if (normalized.contains("content") || normalized.contains("cb")) {
            return "与你常看的品类和标签更匹配";
        }
        if (normalized.contains("rule")) {
            return "与常见搭配关系匹配，适合一起看";
        }
        if (normalized.contains("similar") || normalized.contains("item")) {
            return "和你浏览过的商品在风格与用途上更接近";
        }
        return "结合你的历史行为与偏好为你精选";
    }

    private Map<Long, AnalyticsRecommendationResult> buildSnapshotRowMap(List<AnalyticsRecommendationResult> rows) {
        Map<Long, AnalyticsRecommendationResult> rowMap = new HashMap<>();
        if (rows == null) {
            return rowMap;
        }
        for (AnalyticsRecommendationResult row : rows) {
            if (row != null && row.getProductId() != null) {
                rowMap.putIfAbsent(row.getProductId(), row);
            }
        }
        return rowMap;
    }

    private String resolveSnapshotModelVersion(List<AnalyticsRecommendationResult> rows) {
        AnalyticsRecommendationResult firstRow = firstOrNull(rows);
        return firstRow == null ? null : firstRow.getModelVersion();
    }

    private void enrichExplanationWithCluster(Map<String, Object> result, ClusterContext clusterContext) {
        if (result == null || clusterContext == null) {
            return;
        }
        result.put("segmentCode", clusterContext.segmentCode);
        result.put("segmentName", clusterContext.segmentName);
        result.put("segmentTopCategories", clusterContext.topCategories == null
                ? Collections.emptyList()
                : clusterContext.topCategories);
        result.put("segmentTopTags", clusterContext.topTags == null
                ? Collections.emptyList()
                : clusterContext.topTags);
        result.put("segmentProfile", buildSegmentProfileLabel(clusterContext));
        result.put("strategyHint", buildClusterStrategyHint(clusterContext));
    }

    private void enrichRecommendationNarrative(Map<String, Object> result,
                                               List<Product> products,
                                               ClusterContext clusterContext) {
        if (result == null) {
            return;
        }
        List<Product> safeProducts = products == null ? Collections.emptyList() : products.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("headline", safeProducts.isEmpty()
                ? "当前暂无可展示的推荐结果。"
                : "当前已筛出 " + safeProducts.size() + " 款可优先展示的推荐商品。");
        summary.put("matchLevel", resolveRecommendationMatchLevel(safeProducts, clusterContext));
        summary.put("nextAction", safeProducts.size() >= 2
                ? "适合继续做对比、换一批或按场景缩小范围。"
                : "可以继续补充预算、品牌或使用场景来进一步缩小范围。");
        summary.put("segmentFocus", buildClusterStrategyHint(clusterContext));
        result.put("recommendationSummary", summary);
        result.put("storyCards", buildRecommendationStoryCards(safeProducts, clusterContext));
    }

    private String resolveRecommendationMatchLevel(List<Product> products, ClusterContext clusterContext) {
        if (products == null || products.isEmpty()) {
            return "待生成";
        }
        if (clusterContext != null && !clusterContext.coldStart
                && ((clusterContext.topCategories != null && !clusterContext.topCategories.isEmpty())
                || (clusterContext.topTags != null && !clusterContext.topTags.isEmpty()))) {
            return "高匹配";
        }
        if (products.size() >= 3) {
            return "可探索";
        }
        return "基础匹配";
    }

    private List<Map<String, Object>> buildRecommendationStoryCards(List<Product> products,
                                                                    ClusterContext clusterContext) {
        List<Map<String, Object>> cards = new ArrayList<>();
        Map<String, Object> sourcingCard = new LinkedHashMap<>();
        sourcingCard.put("title", "推荐结果从哪里来");
        sourcingCard.put("summary", buildClusterStrategyHint(clusterContext));
        cards.add(sourcingCard);

        Product leadProduct = firstOrNull(products);
        if (leadProduct != null) {
            Map<String, Object> leadCard = new LinkedHashMap<>();
            leadCard.put("title", "首推商品");
            leadCard.put("summary", firstNonEmpty(
                    leadProduct.getRecommendReason(),
                    "当前首推商品兼顾了匹配度和可转化性。"));
            leadCard.put("productId", leadProduct.getId());
            cards.add(leadCard);
        }
        return cards;
    }

    private String buildSegmentProfileLabel(ClusterContext clusterContext) {
        if (clusterContext == null) {
            return "未分层";
        }
        if (clusterContext.coldStart) {
            return "冷启动";
        }
        if (clusterContext.segmentCode != null) {
            String code = clusterContext.segmentCode.toLowerCase(Locale.ROOT);
            if (code.contains("high")) {
                return "高价值";
            }
            if (code.contains("active")) {
                return "活跃成长";
            }
            if (code.contains("sleep") || code.contains("retain")) {
                return "待唤醒";
            }
        }
        return "稳定客群";
    }

    private String buildSegmentOperationsFocus(AnalyticsKmeansSegment segment, ClusterContext clusterContext) {
        if (segment != null && StringUtils.hasText(segment.getOperationSuggestion())) {
            return segment.getOperationSuggestion();
        }
        return buildClusterStrategyHint(clusterContext);
    }

    private String buildClusterStrategyHint(ClusterContext clusterContext) {
        if (clusterContext == null) {
            return "当前更适合结合热门候选和实时行为信号做基础推荐。";
        }
        if (clusterContext.coldStart) {
            return "当前仍以热门商品和轻量兴趣信号做冷启动承接。";
        }

        String categoryHint = clusterContext.topCategories == null || clusterContext.topCategories.isEmpty()
                ? null
                : "优先承接「" + clusterContext.topCategories.get(0) + "」相关品类";
        String tagHint = clusterContext.topTags == null || clusterContext.topTags.isEmpty()
                ? null
                : "继续放大「" + clusterContext.topTags.get(0) + "」相关兴趣";
        return firstNonEmpty(
                joinNonEmpty("，", categoryHint, tagHint),
                "当前推荐会结合分群结果做差异化排序。");
    }

    private AnalyticsKmeansTask findLatestSuccessfulTaskRecord() {
        return analyticsKmeansTaskMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsKmeansTask>()
                        .eq(AnalyticsKmeansTask::getStatus, TASK_STATUS_SUCCESS)
                        .orderByDesc(AnalyticsKmeansTask::getSnapshotDate)
                        .orderByDesc(AnalyticsKmeansTask::getId)
                        .last("LIMIT 1"));
    }

    private ClusterContext loadClusterContext(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }

        try {
            AnalyticsKmeansTask latestTask = findLatestSuccessfulTaskRecord();
            if (latestTask == null || latestTask.getId() == null) {
                return null;
            }

            AnalyticsKmeansUserResult userResult = analyticsKmeansUserResultMapper.selectOne(
                    new LambdaQueryWrapper<AnalyticsKmeansUserResult>()
                            .eq(AnalyticsKmeansUserResult::getTaskId, latestTask.getId())
                            .eq(AnalyticsKmeansUserResult::getUserId, userId)
                            .last("LIMIT 1"));
            if (userResult == null || !StringUtils.hasText(userResult.getSegmentCode())) {
                return null;
            }

            AnalyticsKmeansSegment segment = analyticsKmeansSegmentMapper.selectOne(
                    new LambdaQueryWrapper<AnalyticsKmeansSegment>()
                            .eq(AnalyticsKmeansSegment::getTaskId, latestTask.getId())
                            .eq(AnalyticsKmeansSegment::getSegmentCode, userResult.getSegmentCode())
                            .last("LIMIT 1"));

            ClusterContext context = new ClusterContext();
            context.segmentCode = userResult.getSegmentCode();
            context.segmentName = userResult.getSegmentName();
            context.coldStart = Objects.equals(userResult.getIsColdStart(), 1)
                    || "COLD_START".equalsIgnoreCase(userResult.getSegmentCode());
            if (segment != null) {
                context.segmentName = StringUtils.hasText(segment.getSegmentName())
                        ? segment.getSegmentName()
                        : context.segmentName;
                context.topCategories = segment.getTopCategories() == null
                        ? Collections.emptyList()
                        : segment.getTopCategories();
                context.topTags = segment.getTopTags() == null
                        ? Collections.emptyList()
                        : segment.getTopTags();
                context.avgPricePerOrder = segment.getAvgPricePerOrder();
            }
            return context;
        } catch (Exception exception) {
            log.warn("[Recommendation] Failed to load cluster context for user {}: {}", userId, exception.getMessage());
            return null;
        }
    }

    private String stringValue(Object rawValue) {
        return rawValue == null ? null : String.valueOf(rawValue);
    }

    private Integer parseInteger(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(rawValue));
        } catch (Exception ignored) {
            return null;
        }
    }

    private double parseDouble(Object rawValue) {
        if (rawValue == null) {
            return 0.0;
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(rawValue));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private List<Product> applyClusterAwareRanking(List<Product> candidates, ClusterContext clusterContext, int limit) {
        return recommendationRerankService.rankByClusterContext(candidates, toRankingContext(clusterContext), limit);
    }

    private List<Product> getClusterAwareFallback(ClusterContext clusterContext, int limit) {
        int safeLimit = normalizeLimit(limit);
        if (clusterContext == null || !clusterContext.hasCategorySignal()) {
            return getDiverseRecommendations(safeLimit);
        }

        List<Map<String, Object>> allCategories = productMapper.selectAllCategoryIds();
        if (allCategories.isEmpty()) {
            return getDiverseRecommendations(safeLimit);
        }

        Map<String, Long> categoryNameIdMap = new LinkedHashMap<>();
        for (Map<String, Object> category : allCategories) {
            Long categoryId = parseLong(category.get("categoryId"));
            Object categoryName = category.get("categoryName");
            if (categoryId != null && categoryName != null) {
                categoryNameIdMap.put(normalizeText(String.valueOf(categoryName)), categoryId);
            }
        }

        List<Product> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int perCategory = Math.max(2, (safeLimit / Math.max(1, clusterContext.topCategories.size())) + 1);
        for (String categoryName : clusterContext.topCategories) {
            Long categoryId = categoryNameIdMap.get(normalizeText(categoryName));
            if (categoryId == null) {
                continue;
            }
            List<Product> categoryProducts = productMapper.selectRandomByCategory(categoryId, perCategory);
            for (Product product : categoryProducts) {
                if (product != null && product.getId() != null && seen.add(product.getId())) {
                    candidates.add(product);
                }
            }
        }

        if (candidates.isEmpty()) {
            return getDiverseRecommendations(safeLimit);
        }

        List<Product> rankedCandidates = applyClusterAwareRanking(candidates, clusterContext, Math.max(safeLimit, candidates.size()));
        List<Product> result = ensureCategoryDiversity(rankedCandidates, safeLimit);
        if (result.size() >= safeLimit) {
            return result;
        }
        return appendFallbackProducts(result, getDiverseRecommendations(safeLimit), safeLimit);
    }

    private List<Product> attachExposureTracking(List<Product> products,
                                                 Long userId,
                                                 String scene,
                                                 String sourceType,
                                                 ClusterContext clusterContext,
                                                 Map<Long, AnalyticsRecommendationResult> snapshotRowMap,
                                                 String fallbackAlgorithm,
                                                 String fallbackModelVersion) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        List<Product> suppressedProducts = applyDislikeSuppression(userId, products, products.size());
        List<Product> fatigueReorderedProducts = applyExposureFatigueReorder(
                suppressedProducts, userId, scene, clusterContext);
        List<Product> sessionDedupProducts = applySessionDedupByScene(
                fatigueReorderedProducts, userId, scene);
        List<Product> displayProducts = applyDisplayRotation(sessionDedupProducts, userId, scene);
        displayProducts = applyFeedDiversityByScene(displayProducts, products.size(), scene);
        if (SCENE_PERSONAL.equals(scene) || SCENE_GUESS_YOU_LIKE.equals(scene)) {
            displayProducts = enforceTopPreferenceCoverage(displayProducts, userId, products.size());
        }

        if (userId == null || userId <= 0) {
            for (Product product : displayProducts) {
                if (product == null) {
                    continue;
                }
                product.setSourceType(sourceType);
                product.setRecommendationSourceType(sourceType);
                product.setModelVersion(SOURCE_LIVE.equals(sourceType) ? "stream-live-v1" : "unknown");
                product.setDataFreshness(SOURCE_LIVE.equals(sourceType) ? "实时" : "未知");
                if (!StringUtils.hasText(product.getRecommendReason())) {
                    product.setRecommendReason(buildDefaultReasonByScene(scene, sourceType));
                }
                if (!StringUtils.hasText(product.getReasonType())) {
                    product.setReasonType(buildDefaultReasonTypeByScene(scene, sourceType));
                }
            }
            return displayProducts;
        }

        String experimentGroup = "disabled";
        if (moduleSwitchService.isEnabled("ab-test")) {
            try {
                experimentGroup = abTestFramework.assignGroup(userId).code;
            } catch (Exception e) {
                log.warn("[Recommendation] A/B分组失败 userId={}: {}", userId, e.getMessage());
                experimentGroup = "disabled";
            }
        }

        String requestToken = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime exposureTime = LocalDateTime.now();
        Map<Long, StreamProductHotnessRealtime> hotnessByProduct = loadRealtimeHotnessMap(displayProducts);
        LocalDateTime latestPreferenceUpdateTime = recommendationRealtimeCacheService.getUserPreferenceLatestUpdateTime(userId);
        LocalDate snapshotDateHint = resolveSnapshotDateHint(snapshotRowMap);
        for (int index = 0; index < displayProducts.size(); index++) {
            Product product = displayProducts.get(index);
            if (product == null || product.getId() == null) {
                continue;
            }

            String exposureToken = UUID.randomUUID().toString().replace("-", "");
            product.setRecommendationToken(exposureToken);
            product.setRecommendationScene(scene);
            product.setRecommendationSegmentCode(clusterContext == null ? null : clusterContext.segmentCode);
            product.setRecommendationSegmentName(clusterContext == null ? null : clusterContext.segmentName);
            product.setRecommendationSourceType(sourceType);

            AnalyticsRecommendationResult snapshotRow = snapshotRowMap.get(product.getId());
            String modelVersion = resolveProductModelVersion(snapshotRow, fallbackModelVersion, sourceType);
            String dataFreshness = resolveDataFreshness(sourceType, scene, snapshotRow,
                    hotnessByProduct.get(product.getId()), latestPreferenceUpdateTime, snapshotDateHint);
            String reasonType = firstNonEmpty(
                    product.getReasonType(),
                    buildDefaultReasonTypeByScene(scene, sourceType));
            if (!StringUtils.hasText(product.getReasonType())) {
                product.setReasonType(reasonType);
            }
            if (!StringUtils.hasText(product.getRecommendReason())) {
                product.setRecommendReason(buildDefaultReasonByScene(scene, sourceType));
            }
            product.setSourceType(sourceType);
            product.setModelVersion(modelVersion);
            product.setDataFreshness(dataFreshness);
            AnalyticsRecommendationExposure exposure = new AnalyticsRecommendationExposure();
            exposure.setExposureToken(exposureToken);
            exposure.setRequestToken(requestToken);
            exposure.setUserId(userId);
            exposure.setProductId(product.getId());
            exposure.setScene(scene);
            exposure.setRankNo(index + 1);
            exposure.setAlgorithm(snapshotRow != null && StringUtils.hasText(snapshotRow.getAlgorithm())
                    ? snapshotRow.getAlgorithm()
                    : fallbackAlgorithm);
            exposure.setSourceType(sourceType);
            exposure.setReasonType(reasonType);
            exposure.setModelVersion(modelVersion);
            exposure.setExperimentGroup(experimentGroup);
            exposure.setSegmentCode(clusterContext == null ? null : clusterContext.segmentCode);
            exposure.setSegmentName(clusterContext == null ? null : clusterContext.segmentName);
            exposure.setExposureTime(exposureTime);

            try {
                analyticsRecommendationExposureMapper.insert(exposure);
            } catch (Exception exception) {
                log.warn("[Recommendation] Failed to persist exposure scene={} user={} product={}: {}",
                        scene, userId, product.getId(), exception.getMessage());
                product.setRecommendationToken(null);
            }
        }

        try {
            List<Long> productIds = displayProducts.stream()
                    .map(Product::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            List<Map<String, Object>> explanations = recommendationExplainer.explain(
                    userId, productIds, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            Map<Long, ReasonPayload> reasonMap = new HashMap<>();
            for (Map<String, Object> exp : explanations) {
                Object pid = exp.get("productId");
                Object text = exp.get("reasonText");
                if (pid != null && text != null) {
                    reasonMap.put(((Number) pid).longValue(),
                            new ReasonPayload(stringValue(exp.get("primaryReason")), text.toString()));
                }
            }
            Map<Long, ReasonPayload> sceneReasonMap = buildSceneReasonMap(userId, scene, sourceType, displayProducts);
            for (Product product : displayProducts) {
                if (product.getId() != null) {
                    ReasonPayload scenePayload = sceneReasonMap.get(product.getId());
                    ReasonPayload explainPayload = reasonMap.get(product.getId());
                    String reason = firstNonEmpty(
                            scenePayload == null ? null : scenePayload.reasonText,
                            explainPayload == null ? null : explainPayload.reasonText,
                            buildDefaultReasonByScene(scene, sourceType));
                    String reasonType = firstNonEmpty(
                            scenePayload == null ? null : scenePayload.reasonType,
                            explainPayload == null ? null : explainPayload.reasonType,
                            buildDefaultReasonTypeByScene(scene, sourceType));
                    product.setRecommendReason(reason);
                    product.setReasonType(reasonType);
                    if (scenePayload != null && scenePayload.matchedTags != null && !scenePayload.matchedTags.isEmpty()) {
                        product.setMatchedReasonTags(scenePayload.matchedTags);
                        product.setReasonSummary(String.join("、", scenePayload.matchedTags));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Recommendation] Failed to generate reasons: {}", e.getMessage());
            for (Product product : displayProducts) {
                if (product == null) {
                    continue;
                }
                if (!StringUtils.hasText(product.getRecommendReason())) {
                    product.setRecommendReason(buildDefaultReasonByScene(scene, sourceType));
                }
                if (!StringUtils.hasText(product.getReasonType())) {
                    product.setReasonType(buildDefaultReasonTypeByScene(scene, sourceType));
                }
            }
        }

        return displayProducts;
    }

    private List<Product> enforceTopPreferenceCoverage(List<Product> products, Long userId, int limit) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? products.size() : limit, products.size()));
        List<String> topCategories = loadUserTopPreferenceCategories(userId, 2);
        if (topCategories.isEmpty()) {
            return products.subList(0, safeLimit);
        }

        List<Product> source = appendTopPreferenceFallbackProducts(
                deduplicateProducts(products), topCategories, Math.max(safeLimit * 3, 30));
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        safeLimit = Math.min(safeLimit, source.size());
        Set<String> topCategorySet = new LinkedHashSet<>(topCategories);
        List<Product> preferred = new ArrayList<>();
        List<Product> exploration = new ArrayList<>();
        for (Product product : source) {
            String category = normalizeInterestCategory(firstNonEmpty(product.getCategoryName(), ""));
            if (topCategorySet.contains(category)) {
                preferred.add(product);
            } else {
                exploration.add(product);
            }
        }

        int inspectSize = Math.min(10, safeLimit);
        int requiredPreferred = Math.min(preferred.size(), (int) Math.ceil(inspectSize * 0.60D));
        List<Product> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        int preferredIndex = 0;
        int explorationIndex = 0;

        String topOneCategory = topCategories.isEmpty() ? "" : topCategories.get(0);
        String secondPreferenceCategory = topCategories.size() > 1 ? topCategories.get(1) : "";
        for (Product product : preferred) {
            String category = normalizeInterestCategory(firstNonEmpty(product.getCategoryName(), ""));
            if (topOneCategory.equals(category) && (product.getId() == null || seen.add(product.getId()))) {
                result.add(product);
                break;
            }
        }
        for (Product product : preferred) {
            String category = normalizeInterestCategory(firstNonEmpty(product.getCategoryName(), ""));
            if (secondPreferenceCategory.equals(category) && (product.getId() == null || seen.add(product.getId()))) {
                result.add(product);
                break;
            }
        }
        int protectedHeadPreferred = Math.min(2, Math.min(requiredPreferred, preferred.size()));
        while (result.size() < protectedHeadPreferred && preferredIndex < preferred.size()) {
            Product product = preferred.get(preferredIndex++);
            if (product.getId() == null || seen.add(product.getId())) {
                result.add(product);
            }
        }
        while (result.size() < requiredPreferred && preferredIndex < preferred.size()) {
            Product product = preferred.get(preferredIndex++);
            if (product.getId() == null || seen.add(product.getId())) {
                result.add(product);
            }
        }
        while (result.size() < safeLimit && explorationIndex < exploration.size()) {
            Product product = exploration.get(explorationIndex++);
            if (product.getId() == null || seen.add(product.getId())) {
                result.add(product);
            }
        }
        while (result.size() < safeLimit && preferredIndex < preferred.size()) {
            Product product = preferred.get(preferredIndex++);
            if (product.getId() == null || seen.add(product.getId())) {
                result.add(product);
            }
        }
        while (result.size() < safeLimit) {
            boolean added = false;
            for (Product product : source) {
                if (product == null || product.getId() == null || !seen.add(product.getId())) {
                    continue;
                }
                result.add(product);
                added = true;
                if (result.size() >= safeLimit) {
                    break;
                }
            }
            if (!added) {
                break;
            }
        }
        applyTopPreferenceReasons(result, topCategories);
        return result;
    }

    private List<Product> appendTopPreferenceFallbackProducts(List<Product> products,
                                                              List<String> topCategories,
                                                              int targetSize) {
        List<Product> result = new ArrayList<>(products == null ? Collections.emptyList() : products);
        if (result.size() >= targetSize || topCategories == null || topCategories.isEmpty()) {
            return result;
        }
        Set<Long> seen = result.stream()
                .filter(Objects::nonNull)
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> fallbackIds = loadTopPreferenceProductIds(topCategories, Math.max(targetSize * 4, 80), seen);
        if (fallbackIds.isEmpty()) {
            return result;
        }
        List<Product> fallbackProducts = mapAvailableProductsByIds(fallbackIds, Math.max(targetSize, fallbackIds.size()));
        for (Product product : fallbackProducts) {
            if (product == null || product.getId() == null || !seen.add(product.getId())) {
                continue;
            }
            result.add(product);
            if (result.size() >= targetSize) {
                break;
            }
        }
        return result;
    }

    private List<Long> loadTopPreferenceProductIds(List<String> topCategories, int limit, Set<Long> excludedIds) {
        if (jdbcTemplate == null || topCategories == null || topCategories.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> topSet = new LinkedHashSet<>(topCategories);
        String categoryInClause = topCategories.stream()
                .filter(StringUtils::hasText)
                .map(this::escapeSqlLiteral)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(categoryInClause)) {
            return Collections.emptyList();
        }
        List<Long> productIds = new ArrayList<>();
        List<Map<String, Object>> rows = queryRecommendationRows(
                "SELECT p.id, COALESCE(c.name, '未分类') AS category " +
                        "FROM product p LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE p.status = 1 AND p.stock > 0 " +
                        "AND COALESCE(c.name, '未分类') IN (" + categoryInClause + ") " +
                        "ORDER BY COALESCE(p.sales_count, 0) DESC, p.id DESC LIMIT " + Math.max(limit, 80));
        for (Map<String, Object> row : rows) {
            Long productId = parseLong(row.get("id"));
            if (productId == null || (excludedIds != null && excludedIds.contains(productId))) {
                continue;
            }
            String category = normalizeInterestCategory(String.valueOf(row.get("category")));
            if (topSet.contains(category)) {
                productIds.add(productId);
                if (productIds.size() >= limit) {
                    break;
                }
            }
        }
        return productIds;
    }

    private String escapeSqlLiteral(String value) {
        return String.valueOf(value == null ? "" : value).replace("'", "''");
    }

    private List<String> loadUserTopPreferenceCategories(Long userId, int limit) {
        if (jdbcTemplate == null || userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        mergePreferenceCategoryRows(scores, queryRecommendationRows(
                "SELECT COALESCE(c.name, '未分类') AS category, " +
                        "SUM(CASE " +
                        "WHEN ub.behavior_type IN ('buy', 'order', 'purchase') THEN 8 " +
                        "WHEN ub.behavior_type IN ('favorite', 'collect') THEN 3 " +
                        "WHEN ub.behavior_type IN ('cart', 'add_cart') THEN 2 " +
                        "WHEN ub.behavior_type = 'search' THEN 2 " +
                        "WHEN ub.behavior_type IN ('remove_cart', 'delete_cart', 'cart_remove', 'dislike') THEN -5 " +
                        "WHEN ub.behavior_type IN ('view', 'browse') THEN 1 ELSE 0 END) AS score " +
                        "FROM user_behavior ub JOIN product p ON ub.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE ub.user_id = ? AND ub.product_id IS NOT NULL GROUP BY COALESCE(c.name, '未分类')",
                userId));
        mergePreferenceCategoryRows(scores, queryRecommendationRows(
                "SELECT COALESCE(c.name, '未分类') AS category, SUM(COALESCE(oi.quantity, 1) * 8) AS score " +
                        "FROM `order` o JOIN order_item oi ON o.id = oi.order_id JOIN product p ON oi.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE o.user_id = ? AND o.status IN (1,2,3) GROUP BY COALESCE(c.name, '未分类')",
                userId));
        mergePreferenceCategoryRows(scores, loadSearchCategoryPreferenceRows(userId));
        mergePreferenceCategoryRows(scores, queryRecommendationRows(
                "SELECT COALESCE(c.name, '未分类') AS category, -SUM(COALESCE(oi.quantity, 1) * 6) AS score " +
                        "FROM refund_request r JOIN order_item oi ON r.order_id = oi.order_id JOIN product p ON oi.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE r.user_id = ? AND COALESCE(r.status, -1) IN (1, 3) GROUP BY COALESCE(c.name, '未分类')",
                userId));
        mergePreferenceCategoryRows(scores, queryRecommendationRows(
                "SELECT COALESCE(c.name, '未分类') AS category, -COUNT(*) * 1.5 AS score " +
                        "FROM analytics_recommendation_exposure e JOIN product p ON e.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE e.user_id = ? AND e.click_time IS NULL AND e.exposure_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                        "GROUP BY COALESCE(c.name, '未分类') HAVING COUNT(*) >= 3",
                userId));

        return scores.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0D)
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private void mergePreferenceCategoryRows(Map<String, Double> scores, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Object rawCategory = firstNonNull(row.get("category"), row.get("category_name"), row.get("categoryName"));
            String category = normalizeInterestCategory(String.valueOf(rawCategory));
            double score = parseDouble(firstNonNull(row.get("score"), row.get("weight"), row.get("preferenceScore")));
            if (StringUtils.hasText(category) && score > 0D) {
                scores.merge(category, score, Double::sum);
            }
        }
    }

    private List<Map<String, Object>> queryRecommendationRows(String sql, Object... args) {
        if (jdbcTemplate == null || !StringUtils.hasText(sql)) {
            return Collections.emptyList();
        }
        try {
            return jdbcTemplate.queryForList(sql, args);
        } catch (Exception exception) {
            log.warn("[Recommendation] preference SQL query failed: {}", exception.getMessage());
            return Collections.emptyList();
        }
    }

    private void applyTopPreferenceReasons(List<Product> products, List<String> topCategories) {
        if (products == null || products.isEmpty() || topCategories == null || topCategories.isEmpty()) {
            return;
        }
        for (Product product : products) {
            if (product == null) {
                continue;
            }
            String category = normalizeInterestCategory(firstNonEmpty(product.getCategoryName(), ""));
            int index = topCategories.indexOf(category);
            if (index == 0) {
                product.setReasonType("TOP1_CATEGORY_MATCH");
                product.setRecommendReason("命中用户 Top1 偏好品类：" + category);
            } else if (index == 1) {
                product.setReasonType("TOP2_CATEGORY_MATCH");
                product.setRecommendReason("命中用户 Top2 偏好品类：" + category);
            } else {
                product.setReasonType("DIVERSITY_EXPLORATION");
                product.setRecommendReason("作为少量多样性探索推荐");
            }
        }
    }

    private String normalizeInterestCategory(String text) {
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text.trim())) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.contains("电脑办公") || normalized.contains("轻办公设备") || normalized.contains("商务办公")
                || normalized.contains("显示设备") || normalized.contains("办公电脑") || normalized.contains("办公键鼠")
                || normalized.contains("显示器") || normalized.contains("键鼠") || normalized.contains("鼠标")
                || normalized.contains("键盘") || normalized.contains("打印机") || normalized.contains("扩展坞")
                || normalized.contains("双屏") || normalized.contains("高刷") || normalized.contains("轻办公")) {
            return "电脑办公";
        }
        if (normalized.contains("图书") || normalized.contains("文具") || normalized.contains("文创")
                || normalized.contains("笔记") || normalized.contains("阅读") || normalized.contains("文学")
                || normalized.contains("小说") || normalized.contains("钢笔") || normalized.contains("中性笔")) {
            return "图书文具";
        }
        if (normalized.contains("食品") || normalized.contains("生鲜") || normalized.contains("饮品")
                || normalized.contains("水果") || normalized.contains("零食") || normalized.contains("咖啡")
                || normalized.contains("茶") || normalized.contains("牛奶") || normalized.contains("矿泉水")
                || normalized.contains("肉脯") || normalized.contains("燕麦")) {
            return "食品生鲜";
        }
        if (normalized.contains("手机") || normalized.contains("数码") || normalized.contains("智能手机")
                || normalized.contains("耳机") || normalized.contains("相机") || normalized.contains("智能手表")
                || normalized.contains("备用机")) {
            return "手机数码";
        }
        if (normalized.contains("家电") || normalized.contains("电器") || normalized.contains("厨房")
                || normalized.contains("厨电") || normalized.contains("净化器") || normalized.contains("洗衣机")
                || normalized.contains("冰箱") || normalized.contains("空调") || normalized.contains("微波炉")
                || normalized.contains("电饭煲") || normalized.contains("台灯")) {
            return "家用电器";
        }
        if (normalized.contains("美妆") || normalized.contains("护肤") || normalized.contains("彩妆")
                || normalized.contains("香水") || normalized.contains("口红") || normalized.contains("精华")
                || normalized.contains("口腔") || normalized.contains("牙刷") || normalized.contains("牙膏")
                || normalized.contains("个护") || normalized.contains("护理")) {
            return "美妆护肤";
        }
        if (normalized.contains("服饰") || normalized.contains("鞋") || normalized.contains("包")
                || normalized.contains("穿搭") || normalized.contains("羽绒服") || normalized.contains("差旅收纳")) {
            return "服饰鞋包";
        }
        if (normalized.contains("运动") || normalized.contains("户外") || normalized.contains("露营")
                || normalized.contains("健身") || normalized.contains("骑行") || normalized.contains("跑步")
                || normalized.contains("羽毛球")) {
            return "运动户外";
        }
        if (normalized.contains("母婴") || normalized.contains("奶瓶") || normalized.contains("纸尿裤")
                || normalized.contains("玩具") || normalized.contains("儿童") || normalized.contains("积木")
                || normalized.contains("安抚")) {
            return "母婴玩具";
        }
        if (normalized.contains("家居") || normalized.contains("家装") || normalized.contains("收纳")
                || normalized.contains("窗帘") || normalized.contains("书架") || normalized.contains("门锁")
                || normalized.contains("地毯") || normalized.contains("安防") || normalized.contains("清洁")) {
            return "家居家装";
        }
        return normalized;
    }

    private Map<Long, ReasonPayload> buildSceneReasonMap(Long userId,
                                                         String scene,
                                                         String sourceType,
                                                         List<Product> products) {
        Map<Long, ReasonPayload> reasonMap = new HashMap<>();
        if (products == null || products.isEmpty()) {
            return reasonMap;
        }

        Map<Long, StreamProductHotnessRealtime> hotnessByProduct = loadRealtimeHotnessMap(products);
        List<Map<String, Object>> realtimePreferences = loadRealtimePreferenceRows(userId);
        String topCategoryName = extractTopCategoryName(realtimePreferences);
        List<String> topPreferenceCategories = loadUserTopPreferenceCategories(userId, 2);
        List<String> shortIntentCategories = loadShortIntentCategories(userId, 2);
        String behaviorType = extractPrimaryBehaviorType(userId);

        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }

            String normalizedProductCategory = normalizeInterestCategory(firstNonEmpty(product.getCategoryName(), ""));
            ReasonPayload naturalPayload = buildNaturalReasonPayload(
                    product, normalizedProductCategory, topPreferenceCategories, shortIntentCategories);
            if (naturalPayload != null) {
                reasonMap.put(product.getId(), naturalPayload);
                continue;
            }
            int preferenceIndex = topPreferenceCategories.indexOf(normalizedProductCategory);
            if (preferenceIndex == 0) {
                reasonMap.put(product.getId(), new ReasonPayload(
                        "TOP1_CATEGORY_MATCH",
                        "命中用户 Top1 偏好品类：" + normalizedProductCategory));
                continue;
            }
            if (preferenceIndex == 1) {
                reasonMap.put(product.getId(), new ReasonPayload(
                        "TOP2_CATEGORY_MATCH",
                        "命中用户 Top2 偏好品类：" + normalizedProductCategory));
                continue;
            }
            if (!topPreferenceCategories.isEmpty()
                    && (SCENE_PERSONAL.equals(scene) || SCENE_GUESS_YOU_LIKE.equals(scene))) {
                reasonMap.put(product.getId(), new ReasonPayload(
                        "DIVERSITY_EXPLORATION",
                        "作为少量多样性探索推荐"));
                continue;
            }

            StreamProductHotnessRealtime hotness = hotnessByProduct.get(product.getId());
            boolean hotTrending = hotness != null && hotness.getHotScore() != null && hotness.getHotScore() > 0D;
            String productCategory = firstNonEmpty(product.getCategoryName(), topCategoryName);
            Map<String, String> templateContext = buildReasonTemplateContext(product);
            RecommendationReasonTemplateLibrary.TemplateResult templateResult =
                    recommendationReasonTemplateLibrary.render(
                            scene, behaviorType, productCategory, sourceType, hotTrending, templateContext);
            reasonMap.put(product.getId(), new ReasonPayload(
                    templateResult.getReasonType(),
                    templateResult.getReasonText()));
        }
        return reasonMap;
    }

    private Map<Long, StreamProductHotnessRealtime> loadRealtimeHotnessMap(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> productIds = products.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return recommendationRealtimeCacheService.getHotRowsByProductIds(productIds);
    }

    private List<String> loadShortIntentCategories(Long userId, int limit) {
        return loadSearchCategoryPreferenceRows(userId).stream()
                .map(row -> firstNonNull(row.get("category_name"), row.get("categoryName")))
                .filter(Objects::nonNull)
                .map(value -> normalizeInterestCategory(String.valueOf(value)))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    private ReasonPayload buildNaturalReasonPayload(Product product,
                                                    String normalizedProductCategory,
                                                    List<String> topPreferenceCategories,
                                                    List<String> shortIntentCategories) {
        if (product == null || product.getId() == null) {
            return null;
        }
        List<String> matchedTags = buildMatchedReasonTags(product, normalizedProductCategory,
                topPreferenceCategories, shortIntentCategories);
        if (matchedTags.isEmpty()) {
            return null;
        }
        boolean hasProfileAnchor = matchedTags.stream()
                .anyMatch(tag -> tag.startsWith("近期搜索:") || tag.startsWith("长期偏好:"));
        if (!hasProfileAnchor) {
            return null;
        }

        String lead = null;
        if (shortIntentCategories != null && shortIntentCategories.contains(normalizedProductCategory)) {
            lead = "近期搜索" + displayCategory(product, normalizedProductCategory);
        } else if (topPreferenceCategories != null && topPreferenceCategories.contains(normalizedProductCategory)) {
            lead = "长期偏好" + displayCategory(product, normalizedProductCategory);
        }
        if (!StringUtils.hasText(lead)) {
            lead = "命中你的兴趣标签";
        }

        List<String> displayTags = matchedTags.stream()
                .filter(tag -> !tag.startsWith("类目命中:") && !tag.startsWith("近期搜索:") && !tag.startsWith("长期偏好:"))
                .map(this::stripReasonTagPrefix)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(2)
                .collect(Collectors.toList());

        String reason = displayTags.isEmpty()
                ? lead + "，推荐这款商品"
                : lead + "，且命中" + String.join("/", displayTags);
        product.setMatchedReasonTags(matchedTags);
        product.setReasonSummary(String.join("、", matchedTags));
        return new ReasonPayload("TAG_MATCH_EXPLAINED", reason, matchedTags);
    }

    private List<String> buildMatchedReasonTags(Product product,
                                                String normalizedProductCategory,
                                                List<String> topPreferenceCategories,
                                                List<String> shortIntentCategories) {
        List<String> matchedTags = new ArrayList<>();
        if (shortIntentCategories != null && shortIntentCategories.contains(normalizedProductCategory)) {
            matchedTags.add("近期搜索:" + displayCategory(product, normalizedProductCategory));
        } else if (topPreferenceCategories != null && topPreferenceCategories.contains(normalizedProductCategory)) {
            matchedTags.add("长期偏好:" + displayCategory(product, normalizedProductCategory));
        }

        List<String> productTags = product.getTags();
        if (productTags != null) {
            for (String rawTag : productTags) {
                if (!StringUtils.hasText(rawTag)) {
                    continue;
                }
                String tag = rawTag.trim();
                if (tag.startsWith("价格:") || tag.startsWith("场景:") || tag.startsWith("细分:")) {
                    matchedTags.add(tag);
                }
                if (matchedTags.size() >= 4) {
                    break;
                }
            }
        }
        return matchedTags.stream().distinct().collect(Collectors.toList());
    }

    private String displayCategory(Product product, String fallback) {
        return firstNonEmpty(product == null ? null : product.getCategoryName(), fallback, "当前品类");
    }

    private String stripReasonTagPrefix(String tag) {
        if (!StringUtils.hasText(tag)) {
            return "";
        }
        int index = tag.indexOf(":");
        if (index < 0) {
            index = tag.indexOf("：");
        }
        return index >= 0 && index < tag.length() - 1 ? tag.substring(index + 1).trim() : tag.trim();
    }

    private Map<String, String> buildReasonTemplateContext(Product product) {
        Map<String, String> context = new HashMap<>();
        if (product == null) {
            return context;
        }
        String priceBand = resolvePriceBand(product.getPrice());
        if (StringUtils.hasText(priceBand)) {
            context.put("priceBand", priceBand);
        }
        if (StringUtils.hasText(product.getMerchantName())) {
            context.put("shop", product.getMerchantName().trim());
        }

        List<String> tags = product.getTags();
        if (tags != null && !tags.isEmpty()) {
            String brand = extractBrandFromTags(tags);
            if (StringUtils.hasText(brand)) {
                context.put("brand", brand);
            }
            String activity = extractActivityFromTags(tags);
            if (StringUtils.hasText(activity)) {
                context.put("activity", activity);
            }
        }
        return context;
    }

    private String resolvePriceBand(BigDecimal price) {
        if (price == null) {
            return null;
        }
        if (price.compareTo(BigDecimal.valueOf(99)) <= 0) {
            return "百元内";
        }
        if (price.compareTo(BigDecimal.valueOf(299)) <= 0) {
            return "百元档";
        }
        if (price.compareTo(BigDecimal.valueOf(799)) <= 0) {
            return "中价位";
        }
        if (price.compareTo(BigDecimal.valueOf(1999)) <= 0) {
            return "中高价位";
        }
        return "高价位";
    }

    private String extractBrandFromTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        for (String rawTag : tags) {
            if (!StringUtils.hasText(rawTag)) {
                continue;
            }
            String normalized = rawTag.trim();
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (lower.startsWith("品牌:") || lower.startsWith("品牌：")) {
                return normalized.substring(3).trim();
            }
            if (lower.startsWith("brand:") || lower.startsWith("brand：")) {
                return normalized.substring(6).trim();
            }
        }
        return null;
    }

    private String extractActivityFromTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        for (String rawTag : tags) {
            if (!StringUtils.hasText(rawTag)) {
                continue;
            }
            String normalized = rawTag.trim();
            if (normalized.contains("秒杀")
                    || normalized.contains("满减")
                    || normalized.contains("优惠")
                    || normalized.contains("活动")
                    || normalized.contains("直降")
                    || normalized.contains("券")) {
                return normalized;
            }
        }
        return null;
    }

    private String extractTopCategoryName(List<Map<String, Object>> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return null;
        }
        Map<String, Object> first = preferences.get(0);
        if (first == null) {
            return null;
        }
        Object categoryName = firstNonNull(first.get("category_name"), first.get("categoryName"));
        if (categoryName != null && StringUtils.hasText(String.valueOf(categoryName))) {
            return String.valueOf(categoryName).trim();
        }
        Object categoryId = firstNonNull(first.get("category_id"), first.get("categoryId"));
        return categoryId == null ? null : String.valueOf(categoryId);
    }

    private String extractPrimaryBehaviorType(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        List<Map<String, Object>> stats = behaviorMapper.selectUserBehaviorStats(userId);
        if (stats == null || stats.isEmpty()) {
            return null;
        }
        Map<String, Object> top = firstOrNull(stats);
        if (top == null) {
            return null;
        }
        String behaviorType = stringValue(top.get("behaviorType"));
        if (!StringUtils.hasText(behaviorType)) {
            behaviorType = stringValue(top.get("behavior_type"));
        }
        if (!StringUtils.hasText(behaviorType)) {
            return null;
        }
        return behaviorType.trim().toLowerCase(Locale.ROOT);
    }

    private String buildDefaultReasonByScene(String scene, String sourceType) {
        RecommendationReasonTemplateLibrary.TemplateResult result =
                recommendationReasonTemplateLibrary.render(scene, null, null, sourceType, SCENE_HOT.equals(scene));
        return result.getReasonText();
    }

    private String buildDefaultReasonTypeByScene(String scene, String sourceType) {
        RecommendationReasonTemplateLibrary.TemplateResult result =
                recommendationReasonTemplateLibrary.render(scene, null, null, sourceType, SCENE_HOT.equals(scene));
        return result.getReasonType();
    }

    private String resolveProductModelVersion(AnalyticsRecommendationResult snapshotRow,
                                              String fallbackModelVersion,
                                              String sourceType) {
        if (snapshotRow != null && StringUtils.hasText(snapshotRow.getModelVersion())) {
            return snapshotRow.getModelVersion();
        }
        if (StringUtils.hasText(fallbackModelVersion)) {
            return fallbackModelVersion;
        }
        return SOURCE_LIVE.equals(sourceType) ? "stream-live-v1" : "unknown";
    }

    private LocalDate resolveSnapshotDateHint(Map<Long, AnalyticsRecommendationResult> snapshotRowMap) {
        if (snapshotRowMap == null || snapshotRowMap.isEmpty()) {
            return null;
        }
        for (AnalyticsRecommendationResult row : snapshotRowMap.values()) {
            if (row != null && row.getSnapshotDate() != null) {
                return row.getSnapshotDate();
            }
        }
        return null;
    }

    private String resolveDataFreshness(String sourceType,
                                        String scene,
                                        AnalyticsRecommendationResult snapshotRow,
                                        StreamProductHotnessRealtime hotnessRow,
                                        LocalDateTime latestPreferenceUpdateTime,
                                        LocalDate snapshotDateHint) {
        if (SOURCE_SNAPSHOT.equals(sourceType)) {
            LocalDate snapshotDate = snapshotRow != null && snapshotRow.getSnapshotDate() != null
                    ? snapshotRow.getSnapshotDate()
                    : snapshotDateHint;
            if (snapshotDate == null) {
                return "离线快照";
            }
            long days = Math.max(0L, ChronoUnit.DAYS.between(snapshotDate.atStartOfDay(), LocalDateTime.now()));
            return "快照(" + days + "天前)";
        }
        if (SCENE_HOT.equals(scene) && hotnessRow != null) {
            String text = formatRelativeFreshness(hotnessRow.getUpdateTime());
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        String preferenceFreshness = formatRelativeFreshness(latestPreferenceUpdateTime);
        if (StringUtils.hasText(preferenceFreshness)) {
            return preferenceFreshness;
        }
        return SOURCE_LIVE.equals(sourceType) ? "实时" : "未知";
    }

    private String formatRelativeFreshness(LocalDateTime updateTime) {
        if (updateTime == null) {
            return null;
        }
        long minutes = ChronoUnit.MINUTES.between(updateTime, LocalDateTime.now());
        if (minutes <= 0) {
            return "实时(<1分钟)";
        }
        if (minutes < 60) {
            return "实时(" + minutes + "分钟)";
        }
        long hours = ChronoUnit.HOURS.between(updateTime, LocalDateTime.now());
        if (hours < 24) {
            return "近" + hours + "小时";
        }
        long days = ChronoUnit.DAYS.between(updateTime, LocalDateTime.now());
        return "近" + days + "天";
    }

    private List<Product> applyDisplayRotation(List<Product> products, Long userId, String scene) {
        List<Product> rotatedProducts = new ArrayList<>(products);
        if (rotatedProducts.size() <= 3) {
            return rotatedProducts;
        }
        if (!isFeedScene(scene)) {
            return rotatedProducts;
        }

        int rotateWindow = Math.min(rotatedProducts.size(), 8);
        int stableHead = 0;
        int movableSize = rotateWindow - stableHead;
        if (movableSize <= 2) {
            return rotatedProducts;
        }

        int seed = Objects.hash(scene, userId == null ? 0L : userId, System.nanoTime());
        int shift = Math.floorMod(seed, movableSize - 1) + 1;
        Collections.rotate(rotatedProducts.subList(stableHead, rotateWindow), -shift);
        return rotatedProducts;
    }

    private List<Product> applySessionDedupByScene(List<Product> products, Long userId, String scene) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        if (!sessionDedupEnabled || userId == null || userId <= 0 || !isFeedScene(scene)) {
            return products;
        }

        int safeLookbackMinutes = Math.max(5, sessionDedupLookbackMinutes);
        int safeHistoryLimit = Math.max(40, sessionDedupHistoryLimit);
        SessionExposureContext context = loadSessionExposureContext(userId, scene, safeLookbackMinutes, safeHistoryLimit);
        if (context.isEmpty()) {
            return products;
        }

        int productThreshold = Math.max(1, sessionDedupMaxProductExposure);
        int categoryThreshold = Math.max(1, sessionDedupMaxCategoryExposure);
        int merchantThreshold = Math.max(1, sessionDedupMaxMerchantExposure);

        List<Product> preferred = new ArrayList<>();
        List<Product> fallback = new ArrayList<>();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            int productExposure = context.productExposureCounter.getOrDefault(product.getId(), 0);
            int categoryExposure = context.categoryExposureCounter.getOrDefault(resolveCategoryKey(product), 0);
            int merchantExposure = context.merchantExposureCounter.getOrDefault(resolveMerchantKey(product), 0);

            if (productExposure >= productThreshold
                    || categoryExposure >= categoryThreshold
                    || merchantExposure >= merchantThreshold) {
                fallback.add(product);
            } else {
                preferred.add(product);
            }
        }

        if (preferred.isEmpty() || fallback.isEmpty()) {
            return products;
        }

        LinkedHashMap<Long, Product> merged = new LinkedHashMap<>();
        for (Product product : preferred) {
            if (product != null && product.getId() != null) {
                merged.putIfAbsent(product.getId(), product);
            }
        }
        for (Product product : fallback) {
            if (product != null && product.getId() != null) {
                merged.putIfAbsent(product.getId(), product);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<Product> applyFeedDiversityByScene(List<Product> products, int limit, String scene) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        if (!shouldApplySceneGuardrails(scene)) {
            return products;
        }
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? products.size() : limit, MAX_LIMIT));
        return applyHomeRecommendationDiversity(products, safeLimit, scene);
    }

    private boolean shouldApplySceneGuardrails(String scene) {
        String flowScene = resolveFlowScene(scene);
        return FLOW_SCENE_HOME.equals(flowScene)
                || FLOW_SCENE_SEARCH.equals(flowScene)
                || FLOW_SCENE_DETAIL.equals(flowScene);
    }

    private List<Product> applyExposureFatigueReorder(List<Product> products,
                                                      Long userId,
                                                      String scene,
                                                      ClusterContext clusterContext) {
        if (products == null || products.size() < ANTI_FATIGUE_MIN_PRODUCT_SIZE || userId == null || userId <= 0) {
            return products == null ? Collections.emptyList() : products;
        }
        if (!isFeedScene(scene)) {
            return products;
        }

        Set<Long> candidateProductIds = products.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (candidateProductIds.isEmpty()) {
            return products;
        }

        Map<Long, Integer> exposureCounter = loadRecentExposureCounter(userId, scene);
        Map<String, Integer> categoryExposureCounter = loadRecentCategoryExposureCounter(userId, scene);
        Map<String, Integer> categoryWeights = buildPreferenceWeightMap(
                clusterContext == null ? Collections.emptyList() : clusterContext.topCategories);
        Map<String, Integer> tagWeights = buildPreferenceWeightMap(
                clusterContext == null ? Collections.emptyList() : clusterContext.topTags);
        SessionIntentContext intentContext = detectSessionIntent(userId, scene);
        NegativeFeedbackContext negativeFeedbackContext = loadFastNegativeFeedbackContext(userId, scene);
        Map<Long, QualityGateSnapshot> qualityGateSnapshotMap = loadQualityGateSnapshots(products, candidateProductIds);
        Map<Long, ProductObjectiveMetrics> objectiveMetrics = loadMultiObjectiveMetrics(candidateProductIds, scene);
        ObjectiveWeightBundle objectiveWeights = resolveObjectiveWeights(intentContext);
        boolean highValueUser = isHighValueUser(clusterContext);
        boolean rollbackActive = shouldEnableAutoRollback(scene);
        double maxPrice = products.stream()
                .filter(Objects::nonNull)
                .map(Product::getPrice)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(0D);
        double maxSales = products.stream()
                .filter(Objects::nonNull)
                .map(Product::getSalesCount)
                .filter(Objects::nonNull)
                .mapToDouble(Integer::doubleValue)
                .max()
                .orElse(0D);

        List<ExposureFatigueScore> scoreRows = new ArrayList<>();
        int size = products.size();
        for (int index = 0; index < size; index++) {
            Product product = products.get(index);
            if (product == null || product.getId() == null) {
                continue;
            }
            int exposureCount = exposureCounter.getOrDefault(product.getId(), 0);
            String categoryKey = resolveCategoryKey(product);
            int categoryExposureCount = categoryExposureCounter.getOrDefault(categoryKey, 0);
            double baseScore = (size - index) * 48.0;
            double fatiguePenalty = exposureCount * 72.0 + (exposureCount >= 2 ? 70.0 : 0.0);
            double saturationPenalty = categoryExposureCount * Math.max(0D, categorySaturationPenalty);
            double noveltyScore = exposureCount <= 0
                    ? Math.max(0D, noveltyBonus)
                    : Math.max(0D, noveltyBonus - exposureCount * 4.0);
            double segmentScore = resolveSegmentAffinityScore(product, categoryWeights, tagWeights);
            double qualityScore = resolveProductQualityScore(product);
            double fastNegativePenalty = resolveFastNegativePenalty(
                    product,
                    categoryKey,
                    negativeFeedbackContext);
            QualityGateSnapshot qualityGateSnapshot = qualityGateSnapshotMap.get(product.getId());
            double qualityGatePenalty = resolveQualityGatePenalty(qualityGateSnapshot, highValueUser);
            double objectiveScore = resolveMultiObjectiveScore(
                    product,
                    objectiveMetrics.get(product.getId()),
                    qualityGateSnapshot,
                    categoryExposureCount,
                    maxPrice,
                    maxSales,
                    objectiveWeights);
            double intentAdjustment = resolveIntentAdjustment(product, intentContext, maxPrice);
            double score = baseScore - fatiguePenalty - saturationPenalty + noveltyScore + segmentScore + qualityScore
                    + objectiveScore + intentAdjustment - fastNegativePenalty - qualityGatePenalty;
            double exploreScore = noveltyScore
                    + Math.max(0D, 18.0 - categoryExposureCount * 4.5)
                    + Math.max(0D, 16.0 - exposureCount * 8.0)
                    + segmentScore * 0.5
                    + objectiveScore * 0.35
                    + intentAdjustment * 0.25
                    - fastNegativePenalty * 0.1
                    - qualityGatePenalty * 0.1;
            scoreRows.add(new ExposureFatigueScore(
                    product,
                    index,
                    score,
                    exposureCount,
                    categoryExposureCount,
                    exploreScore));
        }
        if (scoreRows.isEmpty()) {
            return products;
        }

        scoreRows.sort((left, right) -> {
            int scoreCompare = Double.compare(right.score, left.score);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int exposureCompare = Integer.compare(left.exposureCount, right.exposureCount);
            if (exposureCompare != 0) {
                return exposureCompare;
            }
            int categoryExposureCompare = Integer.compare(left.categoryExposureCount, right.categoryExposureCount);
            if (categoryExposureCompare != 0) {
                return categoryExposureCompare;
            }
            return Integer.compare(left.originalIndex, right.originalIndex);
        });

        if (rollbackActive) {
            log.warn("[Recommendation] auto rollback rerank active, scene={}, reason={}", scene, rerankRollbackReason);
            List<Product> stableProducts = scoreRows.stream()
                    .map(row -> row.product)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return applyCategoryNarrowingGuard(stableProducts, userId, clusterContext);
        }

        List<Product> blendedProducts = applyExplorationBlend(
                scoreRows,
                clusterContext,
                scene,
                userId,
                intentContext);
        return applyCategoryNarrowingGuard(blendedProducts, userId, clusterContext);
    }

    private SessionIntentContext detectSessionIntent(Long userId, String scene) {
        if (!sessionIntentEnabled || userId == null || userId <= 0) {
            return SessionIntentContext.browseDefault();
        }
        int safeLookbackMinutes = Math.max(10, sessionIntentLookbackMinutes);
        int safeHistoryLimit = Math.max(40, sessionIntentMaxHistory);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(safeLookbackMinutes);

        List<UserBehavior> rows = behaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .select(UserBehavior::getBehaviorType,
                                UserBehavior::getProductId,
                                UserBehavior::getSearchKeyword,
                                UserBehavior::getCreateTime)
                        .eq(UserBehavior::getUserId, userId)
                        .ge(UserBehavior::getCreateTime, cutoff)
                        .orderByDesc(UserBehavior::getCreateTime)
                        .last("LIMIT " + safeHistoryLimit));
        if (rows == null || rows.isEmpty()) {
            return SessionIntentContext.browseDefault();
        }

        Map<String, Long> behaviorCounter = new HashMap<>();
        Set<Long> compareProductIds = new LinkedHashSet<>();
        List<String> searchTerms = new ArrayList<>();
        long totalActions = 0L;
        for (UserBehavior row : rows) {
            if (row == null || !StringUtils.hasText(row.getBehaviorType())) {
                continue;
            }
            String behaviorType = normalizeText(row.getBehaviorType());
            behaviorCounter.merge(behaviorType, 1L, Long::sum);
            totalActions++;
            if (Constants.BehaviorType.SEARCH.equals(behaviorType)) {
                searchTerms.addAll(extractSearchTerms(row.getSearchKeyword()));
            }
            if ((Constants.BehaviorType.VIEW.equals(behaviorType)
                    || Constants.BehaviorType.CART.equals(behaviorType)
                    || Constants.BehaviorType.FAVORITE.equals(behaviorType)
                    || Constants.BehaviorType.PURCHASE.equals(behaviorType))
                    && row.getProductId() != null) {
                compareProductIds.add(row.getProductId());
            }
        }

        double behaviorEntropy = computeNormalizedEntropy(behaviorCounter);
        boolean newUser = totalActions <= Math.max(1, explorationNewUserBehaviorThreshold);
        long searchCount = behaviorCounter.getOrDefault(Constants.BehaviorType.SEARCH, 0L);
        if (searchCount > 0L && !searchTerms.isEmpty()) {
            return new SessionIntentContext(
                    SESSION_INTENT_TARGET_SEARCH,
                    0.9D,
                    behaviorEntropy,
                    newUser,
                    searchTerms);
        }

        if (isPriceCompareIntent(compareProductIds)) {
            return new SessionIntentContext(
                    SESSION_INTENT_PRICE_COMPARE,
                    0.75D,
                    behaviorEntropy,
                    newUser,
                    searchTerms);
        }

        return new SessionIntentContext(
                SESSION_INTENT_BROWSE,
                0.6D,
                behaviorEntropy,
                newUser,
                searchTerms);
    }

    private boolean isPriceCompareIntent(Set<Long> compareProductIds) {
        int safeMinProducts = Math.max(2, sessionIntentPriceCompareMinProducts);
        if (compareProductIds == null || compareProductIds.size() < safeMinProducts) {
            return false;
        }
        List<Product> products = productMapper.selectBatchIds(new ArrayList<>(compareProductIds));
        if (products == null || products.size() < safeMinProducts) {
            return false;
        }

        Map<String, Integer> categoryCounter = new HashMap<>();
        List<Double> prices = new ArrayList<>();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            categoryCounter.merge(resolveCategoryKey(product), 1, Integer::sum);
            if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                prices.add(product.getPrice().doubleValue());
            }
        }
        if (prices.size() < safeMinProducts || categoryCounter.isEmpty()) {
            return false;
        }

        int dominantCategoryCount = categoryCounter.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double dominantRatio = (double) dominantCategoryCount / Math.max(1D, products.size());
        if (dominantRatio < 0.6D) {
            return false;
        }

        double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        if (mean <= 0D) {
            return false;
        }
        double variance = prices.stream()
                .mapToDouble(price -> {
                    double delta = price - mean;
                    return delta * delta;
                })
                .average()
                .orElse(0D);
        double cv = Math.sqrt(variance) / mean;
        return cv >= Math.max(0.2D, sessionIntentPriceCompareCvThreshold);
    }

    private List<String> extractSearchTerms(String rawKeyword) {
        if (!StringUtils.hasText(rawKeyword)) {
            return Collections.emptyList();
        }
        String normalized = rawKeyword.trim()
                .replace("，", " ")
                .replace(",", " ")
                .replace("|", " ")
                .replace("/", " ");
        String[] parts = normalized.split("\\s+");
        List<String> terms = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String term = part.trim();
            if (term.length() >= 2) {
                terms.add(term.toLowerCase(Locale.ROOT));
            }
        }
        return terms;
    }

    private double computeNormalizedEntropy(Map<String, Long> counter) {
        if (counter == null || counter.isEmpty()) {
            return 0D;
        }
        double total = counter.values().stream().mapToDouble(value -> value == null ? 0D : value).sum();
        if (total <= 0D) {
            return 0D;
        }
        double entropy = 0D;
        int effectiveTypes = 0;
        for (Long value : counter.values()) {
            if (value == null || value <= 0) {
                continue;
            }
            double probability = value / total;
            entropy += -probability * Math.log(probability);
            effectiveTypes++;
        }
        if (effectiveTypes <= 1) {
            return 0D;
        }
        return clamp(entropy / Math.log(effectiveTypes), 0D, 1D);
    }

    private NegativeFeedbackContext loadFastNegativeFeedbackContext(Long userId, String scene) {
        if (!fastNegativeEnabled || userId == null || userId <= 0) {
            return NegativeFeedbackContext.empty();
        }
        int safeLookbackMinutes = Math.max(5, fastNegativeLookbackMinutes);
        int safeHistoryLimit = Math.max(40, fastNegativeMaxHistory);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(safeLookbackMinutes);

        Map<Long, Integer> productPenaltyCounter = new HashMap<>();
        Set<Long> dislikedProductIds = new LinkedHashSet<>();
        List<UserBehavior> dislikeRows = behaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .select(UserBehavior::getProductId, UserBehavior::getSearchKeyword, UserBehavior::getCreateTime)
                        .eq(UserBehavior::getUserId, userId)
                        .eq(UserBehavior::getBehaviorType, Constants.BehaviorType.DISLIKE)
                        .isNotNull(UserBehavior::getProductId)
                        .ge(UserBehavior::getCreateTime, cutoff)
                        .orderByDesc(UserBehavior::getCreateTime)
                        .last("LIMIT " + safeHistoryLimit));
        if (dislikeRows != null) {
            for (UserBehavior row : dislikeRows) {
                if (row == null || row.getProductId() == null) {
                    continue;
                }
                String reason = RecommendationNegativeFeedbackPolicy.normalizeDislikeReason(row.getSearchKeyword());
                dislikedProductIds.add(row.getProductId());
                productPenaltyCounter.merge(row.getProductId(),
                        RecommendationNegativeFeedbackPolicy.productWeight(reason),
                        Integer::sum);
            }
        }

        List<RecommendationEvent> dwellRows = recommendationEventMapper.selectList(
                new LambdaQueryWrapper<RecommendationEvent>()
                        .select(RecommendationEvent::getProductId,
                                RecommendationEvent::getEventType,
                                RecommendationEvent::getDuration,
                                RecommendationEvent::getCreateTime)
                        .eq(RecommendationEvent::getUserId, userId)
                        .eq(StringUtils.hasText(scene), RecommendationEvent::getScene, scene)
                        .isNotNull(RecommendationEvent::getProductId)
                        .in(RecommendationEvent::getEventType,
                                Arrays.asList(Constants.RecommendationEventType.DWELL, Constants.RecommendationEventType.CLICK))
                        .ge(RecommendationEvent::getCreateTime, cutoff)
                        .orderByDesc(RecommendationEvent::getCreateTime)
                        .last("LIMIT " + safeHistoryLimit));
        int safeShortDwellSeconds = Math.max(2, fastNegativeShortDwellSeconds);
        if (dwellRows != null) {
            for (RecommendationEvent row : dwellRows) {
                if (row == null || row.getProductId() == null) {
                    continue;
                }
                String eventType = normalizeText(row.getEventType());
                int durationSeconds = normalizeDurationSeconds(row.getDuration());
                if ((Constants.RecommendationEventType.DWELL.equals(eventType)
                        || Constants.RecommendationEventType.CLICK.equals(eventType))
                        && durationSeconds >= 0
                        && durationSeconds <= safeShortDwellSeconds) {
                    productPenaltyCounter.merge(row.getProductId(), 1, Integer::sum);
                }
            }
        }

        if (productPenaltyCounter.isEmpty()) {
            return NegativeFeedbackContext.empty();
        }
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productMapper.selectBatchIds(new ArrayList<>(productPenaltyCounter.keySet()))) {
            if (product != null && product.getId() != null) {
                productMap.put(product.getId(), product);
            }
        }
        Map<String, Integer> categoryPenaltyCounter = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : productPenaltyCounter.entrySet()) {
            Product product = productMap.get(entry.getKey());
            String categoryKey = product == null ? "category:unknown" : resolveCategoryKey(product);
            int signalCount = entry.getValue() == null ? 0 : entry.getValue();
            if (signalCount > 0) {
                categoryPenaltyCounter.merge(categoryKey, signalCount, Integer::sum);
            }
        }
        return new NegativeFeedbackContext(productPenaltyCounter, categoryPenaltyCounter, dislikedProductIds);
    }

    private int normalizeDurationSeconds(Integer duration) {
        if (duration == null || duration < 0) {
            return -1;
        }
        int raw = duration;
        if (raw >= 1000) {
            return raw / 1000;
        }
        return raw;
    }

    private double resolveFastNegativePenalty(Product product,
                                              String categoryKey,
                                              NegativeFeedbackContext context) {
        if (product == null || product.getId() == null || context == null || context.isEmpty()) {
            return 0D;
        }
        int productSignal = context.productPenaltyCounter.getOrDefault(product.getId(), 0);
        int categorySignal = context.categoryPenaltyCounter.getOrDefault(categoryKey, 0);
        double penalty = productSignal * Math.max(0D, fastNegativeProductPenalty)
                + categorySignal * Math.max(0D, fastNegativeCategoryPenalty);
        if (context.dislikedProductIds.contains(product.getId())) {
            penalty += Math.max(0D, fastNegativeDislikeExtraPenalty);
        }
        return penalty;
    }

    private Map<Long, QualityGateSnapshot> loadQualityGateSnapshots(List<Product> products, Set<Long> candidateProductIds) {
        if (!qualityGateEnabled || products == null || products.isEmpty()
                || candidateProductIds == null || candidateProductIds.isEmpty()) {
            return Collections.emptyMap();
        }
        int safeLookbackDays = Math.max(3, qualityGateLookbackDays);
        int safeHistoryLimit = Math.max(500, qualityGateMaxHistory);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeLookbackDays);

        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : products) {
            if (product != null && product.getId() != null) {
                productMap.put(product.getId(), product);
            }
        }
        if (productMap.isEmpty()) {
            return Collections.emptyMap();
        }

        List<RecommendationEvent> rows = recommendationEventMapper.selectList(
                new LambdaQueryWrapper<RecommendationEvent>()
                        .select(RecommendationEvent::getProductId,
                                RecommendationEvent::getEventType,
                                RecommendationEvent::getAmount,
                                RecommendationEvent::getCreateTime)
                        .in(RecommendationEvent::getProductId, candidateProductIds)
                        .in(RecommendationEvent::getEventType,
                                Arrays.asList(Constants.RecommendationEventType.ORDER, Constants.RecommendationEventType.REFUND))
                        .ge(RecommendationEvent::getCreateTime, cutoff)
                        .orderByDesc(RecommendationEvent::getCreateTime)
                        .last("LIMIT " + safeHistoryLimit));

        Map<Long, QualityGateAccumulator> accumulators = new HashMap<>();
        if (rows != null) {
            for (RecommendationEvent row : rows) {
                if (row == null || row.getProductId() == null || !productMap.containsKey(row.getProductId())) {
                    continue;
                }
                QualityGateAccumulator accumulator = accumulators.computeIfAbsent(
                        row.getProductId(),
                        ignored -> new QualityGateAccumulator());
                String eventType = normalizeText(row.getEventType());
                if (Constants.RecommendationEventType.ORDER.equals(eventType)) {
                    accumulator.orderCount++;
                    if (row.getAmount() != null && row.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                        accumulator.gmvAmount += row.getAmount().doubleValue();
                    }
                } else if (Constants.RecommendationEventType.REFUND.equals(eventType)) {
                    accumulator.refundCount++;
                }
            }
        }

        int safeLowStockThreshold = Math.max(1, qualityGateLowStockThreshold);
        int safeMinOrderSample = Math.max(1, qualityGateMinOrderSample);
        double safeRefundThreshold = clamp(qualityGateHighRefundRateThreshold, 0.01D, 1D);
        double safeLowRatingThreshold = clamp(qualityGateLowRatingThreshold, 1D, 5D);
        Map<Long, QualityGateSnapshot> snapshotMap = new HashMap<>();
        for (Map.Entry<Long, Product> entry : productMap.entrySet()) {
            Long productId = entry.getKey();
            Product product = entry.getValue();
            QualityGateAccumulator accumulator = accumulators.getOrDefault(productId, new QualityGateAccumulator());
            if (accumulator.orderCount > 0
                    && accumulator.gmvAmount <= 0D
                    && product.getPrice() != null
                    && product.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                accumulator.gmvAmount = accumulator.orderCount * product.getPrice().doubleValue();
            }
            double refundRate = accumulator.orderCount <= 0
                    ? 0D
                    : clamp((double) accumulator.refundCount / (double) accumulator.orderCount, 0D, 1D);
            double rating = product.getRating() == null ? 0D : Math.max(0D, product.getRating().doubleValue());
            int stock = safeInt(product.getStock());
            boolean highRefundRisk = accumulator.orderCount >= safeMinOrderSample && refundRate >= safeRefundThreshold;
            boolean lowRatingRisk = rating > 0D && rating < safeLowRatingThreshold;
            boolean lowStockRisk = product.getStock() != null && stock <= safeLowStockThreshold;
            snapshotMap.put(productId, new QualityGateSnapshot(
                    accumulator.orderCount,
                    accumulator.refundCount,
                    refundRate,
                    accumulator.gmvAmount,
                    rating,
                    stock,
                    highRefundRisk,
                    lowRatingRisk,
                    lowStockRisk));
        }
        return snapshotMap;
    }

    private double resolveQualityGatePenalty(QualityGateSnapshot snapshot, boolean highValueUser) {
        if (snapshot == null) {
            return 0D;
        }
        double penalty = 0D;
        if (snapshot.highRefundRisk) {
            double overflow = Math.max(0D, snapshot.refundRate - qualityGateHighRefundRateThreshold);
            penalty += Math.max(0D, qualityGateRefundPenalty) * (1D + overflow * 3D);
        }
        if (snapshot.lowRatingRisk) {
            double gap = Math.max(0D, qualityGateLowRatingThreshold - snapshot.rating);
            penalty += Math.max(0D, qualityGateRatingPenalty) * (1D + gap / 2D);
        }
        if (snapshot.lowStockRisk) {
            double ratio = 1D;
            if (qualityGateLowStockThreshold > 0) {
                ratio += (qualityGateLowStockThreshold - snapshot.stock) / (double) qualityGateLowStockThreshold;
            }
            penalty += Math.max(0D, qualityGateStockPenalty) * Math.max(1D, ratio);
        }
        if (highValueUser && penalty > 0D) {
            penalty *= clamp(qualityGateHighValuePenaltyMultiplier, 1D, 3D);
        }
        return penalty;
    }

    private boolean isHighValueUser(ClusterContext clusterContext) {
        if (clusterContext == null || clusterContext.avgPricePerOrder == null) {
            return false;
        }
        return clusterContext.avgPricePerOrder.compareTo(BigDecimal.valueOf(qualityGateHighValueAovThreshold)) >= 0;
    }

    private double resolveIntentAdjustment(Product product,
                                           SessionIntentContext intentContext,
                                           double maxPrice) {
        if (product == null || intentContext == null || !StringUtils.hasText(intentContext.intentType)) {
            return 0D;
        }
        if (SESSION_INTENT_TARGET_SEARCH.equals(intentContext.intentType)) {
            return matchSearchIntent(product, intentContext.searchTerms) ? 20D : -8D;
        }
        if (SESSION_INTENT_PRICE_COMPARE.equals(intentContext.intentType)) {
            double price = product.getPrice() == null ? 0D : product.getPrice().doubleValue();
            double lowPriceBonus = (1D - clamp(normalizeLogValue(price, maxPrice), 0D, 1D)) * 16D;
            double ratingBonus = clamp(
                    normalizeLogValue(product.getRating() == null ? 0D : product.getRating().doubleValue(), 5D),
                    0D,
                    1D) * 6D;
            return lowPriceBonus + ratingBonus;
        }
        return 3D;
    }

    private boolean matchSearchIntent(Product product, List<String> searchTerms) {
        if (product == null || searchTerms == null || searchTerms.isEmpty()) {
            return false;
        }
        String name = normalizeText(product.getName());
        String description = normalizeText(product.getDescription());
        List<String> tags = product.getTags();
        String normalizedTags = tags == null
                ? ""
                : normalizeText(String.join(" ", tags));
        for (String searchTerm : searchTerms) {
            if (!StringUtils.hasText(searchTerm)) {
                continue;
            }
            String term = searchTerm.toLowerCase(Locale.ROOT);
            if (name.contains(term) || description.contains(term) || normalizedTags.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldEnableAutoRollback(String scene) {
        if (!rerankAutoRollbackEnabled || !isFeedScene(scene) || streamRealtimeRedisSinkService == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        int safeIntervalSeconds = Math.max(10, rerankAutoRollbackCheckIntervalSeconds);
        if (rerankRollbackLastCheckTime != null
                && ChronoUnit.SECONDS.between(rerankRollbackLastCheckTime, now) < safeIntervalSeconds) {
            return rerankRollbackActive;
        }
        rerankRollbackLastCheckTime = now;
        try {
            Map<String, Object> snapshot = streamRealtimeRedisSinkService.getRecommendationCoreMetrics(null);
            if (snapshot == null || snapshot.isEmpty()) {
                rerankRollbackActive = false;
                rerankRollbackReason = "";
                return false;
            }
            double refundRate = readDouble(snapshot.get("refundRate"));
            double retention7d = readDouble(snapshot.get("retention7d"));
            double repurchaseRate = readDouble(snapshot.get("repurchaseRate"));
            List<String> triggers = new ArrayList<>();
            if (refundRate > 0D && refundRate >= rerankAutoRollbackRefundRateThreshold) {
                triggers.add("refundRate=" + String.format(Locale.ROOT, "%.2f", refundRate));
            }
            if (retention7d > 0D && retention7d <= rerankAutoRollbackRetention7dFloor) {
                triggers.add("retention7d=" + String.format(Locale.ROOT, "%.2f", retention7d));
            }
            if (repurchaseRate > 0D && repurchaseRate <= rerankAutoRollbackRepurchaseRateFloor) {
                triggers.add("repurchaseRate=" + String.format(Locale.ROOT, "%.2f", repurchaseRate));
            }
            rerankRollbackActive = !triggers.isEmpty();
            rerankRollbackReason = rerankRollbackActive ? String.join(", ", triggers) : "";
            return rerankRollbackActive;
        } catch (Exception exception) {
            log.warn("[Recommendation] auto rollback check failed: {}", exception.getMessage());
            rerankRollbackActive = false;
            rerankRollbackReason = "";
            return false;
        }
    }

    private double readDouble(Object rawValue) {
        if (rawValue == null) {
            return 0D;
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(rawValue));
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private Map<Long, Integer> loadRecentExposureCounter(Long userId, String scene) {
        int safeLookbackHours = Math.max(1, exposureLookbackHours);
        int safeHistoryLimit = Math.max(20, exposureMaxHistory);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(safeLookbackHours);
        List<String> sceneScope = resolveExposureSceneScope(scene);
        List<AnalyticsRecommendationExposure> rows = analyticsRecommendationExposureMapper.selectList(
                new LambdaQueryWrapper<AnalyticsRecommendationExposure>()
                        .select(AnalyticsRecommendationExposure::getProductId, AnalyticsRecommendationExposure::getCreateTime)
                        .eq(AnalyticsRecommendationExposure::getUserId, userId)
                        .in(!sceneScope.isEmpty(), AnalyticsRecommendationExposure::getScene, sceneScope)
                        .ge(AnalyticsRecommendationExposure::getCreateTime, cutoff)
                        .orderByDesc(AnalyticsRecommendationExposure::getCreateTime)
                        .last("LIMIT " + safeHistoryLimit));

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Integer> counter = new HashMap<>();
        for (AnalyticsRecommendationExposure row : rows) {
            if (row == null || row.getProductId() == null) {
                continue;
            }
            counter.merge(row.getProductId(), 1, Integer::sum);
        }
        return counter;
    }

    private SessionExposureContext loadSessionExposureContext(Long userId,
                                                              String scene,
                                                              int lookbackMinutes,
                                                              int historyLimit) {
        if (userId == null || userId <= 0) {
            return SessionExposureContext.empty();
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, lookbackMinutes));
        List<String> sceneScope = resolveExposureSceneScope(scene);
        List<AnalyticsRecommendationExposure> rows = analyticsRecommendationExposureMapper.selectList(
                new LambdaQueryWrapper<AnalyticsRecommendationExposure>()
                        .select(AnalyticsRecommendationExposure::getProductId, AnalyticsRecommendationExposure::getCreateTime)
                        .eq(AnalyticsRecommendationExposure::getUserId, userId)
                        .in(!sceneScope.isEmpty(), AnalyticsRecommendationExposure::getScene, sceneScope)
                        .isNotNull(AnalyticsRecommendationExposure::getProductId)
                        .ge(AnalyticsRecommendationExposure::getCreateTime, cutoff)
                        .orderByDesc(AnalyticsRecommendationExposure::getCreateTime)
                        .last("LIMIT " + Math.max(20, historyLimit)));
        if (rows == null || rows.isEmpty()) {
            return SessionExposureContext.empty();
        }

        Map<Long, Integer> productExposureCounter = new HashMap<>();
        Set<Long> productIds = new LinkedHashSet<>();
        for (AnalyticsRecommendationExposure row : rows) {
            if (row == null || row.getProductId() == null) {
                continue;
            }
            productIds.add(row.getProductId());
            productExposureCounter.merge(row.getProductId(), 1, Integer::sum);
        }
        if (productIds.isEmpty()) {
            return SessionExposureContext.empty();
        }

        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productMapper.selectBatchIds(new ArrayList<>(productIds))) {
            if (product != null && product.getId() != null) {
                productMap.put(product.getId(), product);
            }
        }

        Map<String, Integer> categoryExposureCounter = new HashMap<>();
        Map<String, Integer> merchantExposureCounter = new HashMap<>();
        for (AnalyticsRecommendationExposure row : rows) {
            if (row == null || row.getProductId() == null) {
                continue;
            }
            Product product = productMap.get(row.getProductId());
            if (product == null) {
                continue;
            }
            categoryExposureCounter.merge(resolveCategoryKey(product), 1, Integer::sum);
            merchantExposureCounter.merge(resolveMerchantKey(product), 1, Integer::sum);
        }

        return new SessionExposureContext(
                productExposureCounter,
                categoryExposureCounter,
                merchantExposureCounter);
    }

    private Map<String, Integer> loadRecentCategoryExposureCounter(Long userId, String scene) {
        int safeLookbackHours = Math.max(1, exposureLookbackHours);
        int safeHistoryLimit = Math.max(20, exposureMaxHistory);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(safeLookbackHours);
        List<String> sceneScope = resolveExposureSceneScope(scene);
        List<AnalyticsRecommendationExposure> rows = analyticsRecommendationExposureMapper.selectList(
                new LambdaQueryWrapper<AnalyticsRecommendationExposure>()
                        .select(AnalyticsRecommendationExposure::getProductId, AnalyticsRecommendationExposure::getCreateTime)
                        .eq(AnalyticsRecommendationExposure::getUserId, userId)
                        .in(!sceneScope.isEmpty(), AnalyticsRecommendationExposure::getScene, sceneScope)
                        .isNotNull(AnalyticsRecommendationExposure::getProductId)
                        .ge(AnalyticsRecommendationExposure::getCreateTime, cutoff)
                        .orderByDesc(AnalyticsRecommendationExposure::getCreateTime)
                        .last("LIMIT " + safeHistoryLimit));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> productIds = rows.stream()
                .map(AnalyticsRecommendationExposure::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productMapper.selectBatchIds(new ArrayList<>(productIds))) {
            if (product != null && product.getId() != null) {
                productMap.put(product.getId(), product);
            }
        }

        Map<String, Integer> categoryCounter = new HashMap<>();
        for (AnalyticsRecommendationExposure row : rows) {
            if (row == null || row.getProductId() == null) {
                continue;
            }
            Product product = productMap.get(row.getProductId());
            String categoryKey = product == null
                    ? "category:unknown"
                    : resolveCategoryKey(product);
            categoryCounter.merge(categoryKey, 1, Integer::sum);
        }
        return categoryCounter;
    }

    private List<String> resolveExposureSceneScope(String scene) {
        String normalized = normalizeFeedScene(scene);
        if (isFeedScene(normalized)) {
            return Arrays.asList(
                    SCENE_HOT,
                    SCENE_GUESS_YOU_LIKE,
                    SCENE_PERSONAL,
                    SCENE_COLLABORATIVE_FILTERING);
        }
        return StringUtils.hasText(normalized)
                ? Collections.singletonList(normalized)
                : Collections.emptyList();
    }

    private boolean isFeedScene(String scene) {
        String normalized = normalizeText(scene);
        return SCENE_HOT.equals(normalized)
                || SCENE_GUESS_YOU_LIKE.equals(normalized)
                || SCENE_PERSONAL.equals(normalized)
                || SCENE_COLLABORATIVE_FILTERING.equals(normalized);
    }

    private double resolveSegmentAffinityScore(Product product,
                                               Map<String, Integer> categoryWeights,
                                               Map<String, Integer> tagWeights) {
        if (product == null) {
            return 0D;
        }

        double score = 0D;
        String normalizedCategoryName = normalizeText(product.getCategoryName());
        Integer categoryWeight = categoryWeights.get(normalizedCategoryName);
        if (categoryWeight != null) {
            score += categoryWeight * 6.5;
        }

        if (tagWeights != null && !tagWeights.isEmpty() && product.getTags() != null) {
            for (String tag : product.getTags()) {
                Integer tagWeight = tagWeights.get(normalizeText(tag));
                if (tagWeight != null) {
                    score += tagWeight * 2.2;
                }
            }
        }
        return score;
    }

    private double resolveProductQualityScore(Product product) {
        if (product == null) {
            return 0D;
        }
        double score = 0D;
        if (product.getRating() != null) {
            score += Math.max(0D, product.getRating().doubleValue()) * 3.2;
        }
        if (product.getSalesCount() != null && product.getSalesCount() > 0) {
            score += Math.log1p(product.getSalesCount()) * 2.0;
        }
        if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            score += Math.min(6D, Math.log1p(product.getPrice().doubleValue()));
        }
        return score;
    }

    private Map<Long, ProductObjectiveMetrics> loadMultiObjectiveMetrics(Set<Long> candidateProductIds, String scene) {
        if (candidateProductIds == null || candidateProductIds.isEmpty()) {
            return Collections.emptyMap();
        }
        int safeLookbackDays = Math.max(1, multiObjectiveMetricLookbackDays);
        int safeMaxRows = Math.max(200, multiObjectiveMetricMaxRows);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeLookbackDays);
        List<AnalyticsRecommendationExposure> rows = analyticsRecommendationExposureMapper.selectList(
                new LambdaQueryWrapper<AnalyticsRecommendationExposure>()
                        .select(AnalyticsRecommendationExposure::getProductId,
                                AnalyticsRecommendationExposure::getClickTime,
                                AnalyticsRecommendationExposure::getPurchaseTime,
                                AnalyticsRecommendationExposure::getCreateTime)
                        .in(AnalyticsRecommendationExposure::getProductId, candidateProductIds)
                        .eq(StringUtils.hasText(scene), AnalyticsRecommendationExposure::getScene, scene)
                        .ge(AnalyticsRecommendationExposure::getCreateTime, cutoff)
                        .orderByDesc(AnalyticsRecommendationExposure::getCreateTime)
                        .last("LIMIT " + safeMaxRows));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ProductObjectiveMetrics> metricsMap = new HashMap<>();
        for (AnalyticsRecommendationExposure row : rows) {
            if (row == null || row.getProductId() == null) {
                continue;
            }
            ProductObjectiveMetrics metrics = metricsMap.computeIfAbsent(row.getProductId(), key -> new ProductObjectiveMetrics());
            metrics.exposureCount++;
            if (row.getClickTime() != null) {
                metrics.clickCount++;
            }
            if (row.getPurchaseTime() != null) {
                metrics.purchaseCount++;
            }
        }
        return metricsMap;
    }

    /**
     * 多目标公式（可配权重）：
     * score = boostScale * (w1*ctr + w2*cvr + w3*gmv + w4*diversity - w5*refundPenalty)
     */
    private double resolveMultiObjectiveScore(Product product,
                                              ProductObjectiveMetrics metrics,
                                              QualityGateSnapshot qualitySnapshot,
                                              int categoryExposureCount,
                                              double maxPrice,
                                              double maxSales,
                                              ObjectiveWeightBundle weights) {
        if (product == null || weights == null || weights.totalWeight <= 0D) {
            return 0D;
        }
        int minSample = Math.max(1, multiObjectiveMinExposureSample);
        int exposure = metrics == null ? 0 : metrics.exposureCount;
        int click = metrics == null ? 0 : metrics.clickCount;
        int purchase = metrics == null ? 0 : metrics.purchaseCount;

        double ctrProxy;
        double cvrProxy;
        if (exposure >= minSample) {
            ctrProxy = clamp((double) click / exposure, 0D, 1D);
            cvrProxy = click > 0
                    ? clamp((double) purchase / click, 0D, 1D)
                    : clamp((double) purchase / Math.max(1D, exposure), 0D, 1D);
        } else {
            ctrProxy = clamp(normalizeLogValue(safeInt(product.getSalesCount()), maxSales), 0D, 1D);
            cvrProxy = clamp(((ctrProxy * 0.55)
                    + clamp(normalizeLogValue(product.getRating() == null ? 0D : product.getRating().doubleValue(), 5D), 0D, 1D) * 0.45), 0D, 1D);
        }

        double gmvProxy = clamp(normalizeLogValue(
                qualitySnapshot != null && qualitySnapshot.orderCount > 0 && qualitySnapshot.gmvAmount > 0D
                        ? qualitySnapshot.gmvAmount / qualitySnapshot.orderCount
                        : (product.getPrice() == null ? 0D : product.getPrice().doubleValue()) * Math.max(0.1D, cvrProxy),
                Math.max(1D, maxPrice)), 0D, 1D);
        double diversityProxy = clamp(1D / (1D + Math.max(0, categoryExposureCount)), 0D, 1D);
        double refundPenaltyProxy = qualitySnapshot == null
                ? 0D
                : clamp(qualitySnapshot.refundRate, 0D, 1D);
        double blended = ctrProxy * weights.ctrWeight
                + cvrProxy * weights.cvrWeight
                + gmvProxy * weights.gmvWeight
                + diversityProxy * weights.diversityWeight
                - refundPenaltyProxy * weights.refundPenaltyWeight;
        return clamp(blended, -0.6D, 1D) * Math.max(0D, multiObjectiveBoostScale);
    }

    private ObjectiveWeightBundle resolveObjectiveWeights(SessionIntentContext intentContext) {
        double ctr = Math.max(0D, multiObjectiveCtrWeight);
        double cvr = Math.max(0D, multiObjectiveOrderRateWeight);
        double gmv = Math.max(0D, multiObjectiveGmvWeight > 0D ? multiObjectiveGmvWeight : multiObjectiveAovWeight);
        double diversity = Math.max(0D, multiObjectiveDiversityWeight);
        double refundPenalty = Math.max(0D, multiObjectiveRefundPenaltyWeight);

        if (intentContext != null && StringUtils.hasText(intentContext.intentType)) {
            if (SESSION_INTENT_TARGET_SEARCH.equals(intentContext.intentType)) {
                cvr += 0.08D;
                gmv += 0.03D;
                diversity = Math.max(0D, diversity - 0.04D);
            } else if (SESSION_INTENT_PRICE_COMPARE.equals(intentContext.intentType)) {
                cvr += 0.05D;
                diversity += 0.03D;
                ctr = Math.max(0D, ctr - 0.02D);
            } else {
                diversity += 0.05D;
            }
        }

        double total = ctr + cvr + gmv + diversity + refundPenalty;
        if (total <= 0D) {
            return new ObjectiveWeightBundle(0.26, 0.30, 0.14, 0.20, 0.10, 1.0);
        }
        return new ObjectiveWeightBundle(
                ctr / total,
                cvr / total,
                gmv / total,
                diversity / total,
                refundPenalty / total,
                total);
    }

    private double normalizeLogValue(double value, double maxValue) {
        double safeValue = Math.max(0D, value);
        double safeMax = Math.max(0D, maxValue);
        if (safeMax <= 0D) {
            return 0D;
        }
        double numerator = Math.log1p(safeValue);
        double denominator = Math.log1p(safeMax);
        if (denominator <= 0D) {
            return 0D;
        }
        return numerator / denominator;
    }

    private double resolveFreshnessProxy(LocalDateTime createTime) {
        if (createTime == null) {
            return 0D;
        }
        long days = Math.max(0L, ChronoUnit.DAYS.between(createTime, LocalDateTime.now()));
        if (days <= 7L) {
            return 1.0;
        }
        if (days <= 30L) {
            return 1D - ((days - 7D) / 23D) * 0.55D;
        }
        if (days <= 90L) {
            return Math.max(0.2D, 0.45D - ((days - 30D) / 60D) * 0.25D);
        }
        return 0.15D;
    }

    private List<Product> applyExplorationBlend(List<ExposureFatigueScore> rankedRows,
                                                ClusterContext clusterContext,
                                                String scene,
                                                Long userId,
                                                SessionIntentContext intentContext) {
        if (rankedRows == null || rankedRows.isEmpty()) {
            return Collections.emptyList();
        }

        double safeBaseExploreRate = clamp(explorationBaseRate, 0D, 0.08D);
        boolean coldStart = clusterContext == null || clusterContext.coldStart || !clusterContext.hasCategorySignal();
        double exploreRate = coldStart
                ? safeBaseExploreRate + clamp(explorationColdStartBonus, 0D, 0.03D)
                : safeBaseExploreRate;
        if (explorationDynamicEnabled && intentContext != null) {
            if (intentContext.newUser) {
                exploreRate += clamp(explorationNewUserBonus, 0D, 0.03D);
            }
            double entropyThreshold = clamp(explorationLowEntropyThreshold, 0.05D, 0.95D);
            if (intentContext.behaviorEntropy > 0D && intentContext.behaviorEntropy < entropyThreshold) {
                double deficit = (entropyThreshold - intentContext.behaviorEntropy) / entropyThreshold;
                exploreRate += clamp(explorationLowEntropyBonus, 0D, 0.03D) * clamp(deficit, 0D, 1D);
            }
            if (SESSION_INTENT_TARGET_SEARCH.equals(intentContext.intentType)) {
                exploreRate -= clamp(explorationHighIntentPenalty, 0D, 0.30D);
            } else if (SESSION_INTENT_PRICE_COMPARE.equals(intentContext.intentType)) {
                exploreRate -= clamp(explorationCompareIntentPenalty, 0D, 0.20D);
            }
        }
        if (SCENE_HOT.equals(scene)) {
            exploreRate += 0.01D;
        }
        if (userId == null || userId <= 0) {
            exploreRate += 0.01D;
        }
        exploreRate = clamp(exploreRate, 0D, 0.08D);

        int maxSlots = Math.max(1, Math.min(2, maxExploreSlots));
        int exploreSlots = Math.min(maxSlots, (int) Math.round(rankedRows.size() * exploreRate));
        if (exploreSlots <= 0) {
            return rankedRows.stream().map(row -> row.product).collect(Collectors.toList());
        }

        List<ExposureFatigueScore> exploreCandidates = rankedRows.stream()
                .filter(row -> row.exposureCount <= 1 || row.categoryExposureCount <= 1)
                .sorted((left, right) -> {
                    int compare = Double.compare(right.exploreScore, left.exploreScore);
                    if (compare != 0) {
                        return compare;
                    }
                    return Double.compare(right.score, left.score);
                })
                .collect(Collectors.toList());
        if (exploreCandidates.isEmpty()) {
            return rankedRows.stream().map(row -> row.product).collect(Collectors.toList());
        }

        Set<Long> selectedExploreIds = new LinkedHashSet<>();
        List<Product> exploreProducts = new ArrayList<>();
        for (ExposureFatigueScore candidate : exploreCandidates) {
            if (candidate.product == null || candidate.product.getId() == null) {
                continue;
            }
            if (selectedExploreIds.add(candidate.product.getId())) {
                exploreProducts.add(candidate.product);
                if (exploreProducts.size() >= exploreSlots) {
                    break;
                }
            }
        }
        if (exploreProducts.isEmpty()) {
            return rankedRows.stream().map(row -> row.product).collect(Collectors.toList());
        }

        int exploitTargetSize = Math.max(1, rankedRows.size() - exploreProducts.size());
        List<Product> exploitProducts = new ArrayList<>();
        for (ExposureFatigueScore row : rankedRows) {
            if (row.product == null || row.product.getId() == null) {
                continue;
            }
            if (selectedExploreIds.contains(row.product.getId())) {
                continue;
            }
            exploitProducts.add(row.product);
            if (exploitProducts.size() >= exploitTargetSize) {
                break;
            }
        }

        List<Product> blended = new ArrayList<>();
        int insertInterval = Math.max(2, exploitProducts.size() / Math.max(1, exploreProducts.size()));
        int exploreIndex = 0;
        for (int index = 0; index < exploitProducts.size(); index++) {
            blended.add(exploitProducts.get(index));
            if ((index + 1) % insertInterval == 0 && exploreIndex < exploreProducts.size()) {
                blended.add(exploreProducts.get(exploreIndex++));
            }
        }
        while (exploreIndex < exploreProducts.size()) {
            blended.add(exploreProducts.get(exploreIndex++));
        }

        Set<Long> seen = new LinkedHashSet<>();
        List<Product> deduplicated = new ArrayList<>();
        for (Product product : blended) {
            if (product == null || product.getId() == null) {
                continue;
            }
            if (seen.add(product.getId())) {
                deduplicated.add(product);
            }
        }
        for (ExposureFatigueScore row : rankedRows) {
            if (row.product == null || row.product.getId() == null) {
                continue;
            }
            if (seen.add(row.product.getId())) {
                deduplicated.add(row.product);
            }
        }
        return deduplicated;
    }

    private List<Product> applyCategoryNarrowingGuard(List<Product> products, Long userId, ClusterContext clusterContext) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        List<Product> deduplicated = deduplicateProducts(products);
        if (deduplicated.size() < 4) {
            return deduplicated;
        }
        if (!shouldApplyNarrowGuard(userId)) {
            return deduplicated;
        }

        double minEntropy = clamp(narrowGuardMinCategoryEntropy, 0D, 1D);
        double maxSingleCategoryRatio = clamp(narrowGuardMaxSingleCategoryRatio, 0.35D, 0.95D);

        DistributionMetric baseline = evaluateCategoryDistribution(deduplicated);
        if (isDistributionWithinGuard(baseline, minEntropy, maxSingleCategoryRatio)) {
            return deduplicated;
        }

        int resultSize = deduplicated.size();
        int protectedHeadSize = Math.min(resultSize, Math.max(0, narrowGuardProtectedHeadSize));
        int maxAdjustRounds = Math.max(6, Math.min(300, narrowGuardMaxAdjustRounds));
        int maxSingleCategoryCount = Math.max(1, (int) Math.floor(resultSize * maxSingleCategoryRatio));

        List<Product> candidatePool = appendFallbackProducts(
                new ArrayList<>(),
                getClusterAwareFallback(clusterContext, Math.max(resultSize * 3, 24)),
                Math.max(resultSize * 3, 24));
        candidatePool = appendFallbackProducts(
                candidatePool,
                getDiverseRecommendations(Math.max(resultSize * 4, 32)),
                Math.max(resultSize * 5, 40));
        if (candidatePool.isEmpty()) {
            return deduplicated;
        }

        List<Product> adjusted = new ArrayList<>(deduplicated);
        for (int round = 0; round < maxAdjustRounds; round++) {
            DistributionMetric metric = evaluateCategoryDistribution(adjusted);
            if (isDistributionWithinGuard(metric, minEntropy, maxSingleCategoryRatio)) {
                break;
            }
            int replaceIndex = findReplaceableIndex(adjusted, protectedHeadSize, metric.dominantCategoryKey);
            if (replaceIndex < 0) {
                break;
            }
            Product replacement = pickReplacementCandidate(
                    candidatePool, adjusted, metric.categoryCounter, metric.dominantCategoryKey, maxSingleCategoryCount);
            if (replacement == null) {
                break;
            }
            adjusted.set(replaceIndex, replacement);
        }

        DistributionMetric finalMetric = evaluateCategoryDistribution(adjusted);
        boolean improved = finalMetric.normalizedEntropy > baseline.normalizedEntropy
                || finalMetric.maxCategoryRatio < baseline.maxCategoryRatio;
        return improved ? adjusted : deduplicated;
    }

    private boolean shouldApplyNarrowGuard(Long userId) {
        if (!narrowGuardEnabled) {
            return false;
        }
        if (!narrowGuardAbEnabled) {
            return true;
        }
        if (userId == null || userId <= 0) {
            return true;
        }
        if (!moduleSwitchService.isEnabled("ab-test")) {
            return true;
        }
        Set<String> treatmentGroups = parseTreatmentGroups(narrowGuardTreatmentGroups);
        if (treatmentGroups.isEmpty()) {
            return true;
        }
        try {
            String groupCode = abTestFramework.assignGroup(userId).code;
            return treatmentGroups.contains(normalizeText(groupCode));
        } catch (Exception e) {
            return true;
        }
    }

    private Set<String> parseTreatmentGroups(String rawGroups) {
        if (!StringUtils.hasText(rawGroups)) {
            return Collections.emptySet();
        }
        Set<String> groups = new HashSet<>();
        String[] parts = rawGroups.split("[,，\\s]+");
        for (String part : parts) {
            String normalized = normalizeText(part);
            if (!normalized.isEmpty()) {
                groups.add(normalized);
            }
        }
        return groups;
    }

    private boolean isDistributionWithinGuard(DistributionMetric metric, double minEntropy, double maxSingleCategoryRatio) {
        if (metric == null) {
            return true;
        }
        return metric.normalizedEntropy >= minEntropy && metric.maxCategoryRatio <= maxSingleCategoryRatio;
    }

    private int findReplaceableIndex(List<Product> products, int protectedHeadSize, String dominantCategoryKey) {
        if (products == null || products.isEmpty()) {
            return -1;
        }
        for (int index = products.size() - 1; index >= protectedHeadSize; index--) {
            Product product = products.get(index);
            if (product == null || product.getId() == null) {
                continue;
            }
            if (Objects.equals(resolveCategoryKey(product), dominantCategoryKey)) {
                return index;
            }
        }
        return -1;
    }

    private Product pickReplacementCandidate(List<Product> candidatePool,
                                             List<Product> selectedProducts,
                                             Map<String, Integer> categoryCounter,
                                             String dominantCategoryKey,
                                             int maxSingleCategoryCount) {
        if (candidatePool == null || candidatePool.isEmpty()) {
            return null;
        }
        Set<Long> selectedIds = selectedProducts.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Product fallback = null;
        for (Product candidate : candidatePool) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            if (selectedIds.contains(candidate.getId())) {
                continue;
            }
            String candidateCategoryKey = resolveCategoryKey(candidate);
            int categoryCount = categoryCounter.getOrDefault(candidateCategoryKey, 0);
            if (categoryCount >= maxSingleCategoryCount) {
                continue;
            }
            if (!Objects.equals(candidateCategoryKey, dominantCategoryKey)) {
                return candidate;
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    private List<Product> deduplicateProducts(List<Product> products) {
        LinkedHashMap<Long, Product> deduplicated = new LinkedHashMap<>();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            deduplicated.putIfAbsent(product.getId(), product);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private DistributionMetric evaluateCategoryDistribution(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return new DistributionMetric(Collections.emptyMap(), 1D, 0D, "");
        }
        Map<String, Integer> categoryCounter = new LinkedHashMap<>();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            String categoryKey = resolveCategoryKey(product);
            categoryCounter.merge(categoryKey, 1, Integer::sum);
        }
        if (categoryCounter.isEmpty()) {
            return new DistributionMetric(Collections.emptyMap(), 1D, 0D, "");
        }

        int total = categoryCounter.values().stream().mapToInt(Integer::intValue).sum();
        int maxCount = 0;
        String dominantCategoryKey = "";
        double entropy = 0D;
        for (Map.Entry<String, Integer> entry : categoryCounter.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count <= 0) {
                continue;
            }
            double proportion = (double) count / (double) Math.max(1, total);
            entropy += -proportion * Math.log(proportion);
            if (count > maxCount) {
                maxCount = count;
                dominantCategoryKey = entry.getKey();
            }
        }

        int distinctCategorySize = categoryCounter.size();
        double normalizedEntropy = distinctCategorySize <= 1
                ? 0D
                : clamp(entropy / Math.log(distinctCategorySize), 0D, 1D);
        double maxCategoryRatio = clamp((double) maxCount / (double) Math.max(1, total), 0D, 1D);
        return new DistributionMetric(categoryCounter, normalizedEntropy, maxCategoryRatio, dominantCategoryKey);
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private Map<String, Integer> buildPreferenceWeightMap(List<String> rawValues) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return weights;
        }
        int weight = rawValues.size();
        for (String rawValue : rawValues) {
            String normalized = normalizeText(rawValue);
            if (!normalized.isEmpty()) {
                weights.putIfAbsent(normalized, weight);
                weight = Math.max(1, weight - 1);
            }
        }
        return weights;
    }

    private String normalizeText(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "";
        }
        return rawValue.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveNearDuplicateKey(Product product) {
        if (product == null) {
            return "duplicate:unknown";
        }
        String categoryKey = resolveCategoryKey(product);
        String titleKey = normalizeProductNameForDuplicate(product.getName());
        String imageKey = normalizeImageForDuplicate(product.getImage());
        if (titleKey.isEmpty() && imageKey.isEmpty()) {
            return "duplicate:fallback:" + (product.getId() == null ? 0L : product.getId());
        }
        return categoryKey + "|title:" + titleKey + "|image:" + imageKey;
    }

    private String normalizeProductNameForDuplicate(String productName) {
        String normalized = normalizeText(productName);
        if (normalized.isEmpty()) {
            return "";
        }

        String stripped = normalized
                .replaceAll("(?i)\\b(20\\d{2}|[vx]?\\d+[a-z0-9\\-]*)\\b", " ")
                .replaceAll("(?i)(官方|旗舰|正品|新品|热卖|包邮|同款|轻享版|升级版)", " ")
                .replaceAll("[\\p{Punct}\\s]+", " ")
                .trim();

        String hanOnly = stripped.replaceAll("[^\\p{IsHan}]", "");
        if (hanOnly.length() >= 4) {
            return hanOnly.length() > 14 ? hanOnly.substring(0, 14) : hanOnly;
        }

        String compact = stripped.replaceAll("\\s+", "");
        if (compact.length() > 18) {
            return compact.substring(0, 18);
        }
        return compact;
    }

    private String normalizeImageForDuplicate(String imageUrl) {
        String normalized = normalizeText(imageUrl);
        if (normalized.isEmpty()) {
            return "";
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < normalized.length()) {
            normalized = normalized.substring(slashIndex + 1);
        }
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex > 0) {
            normalized = normalized.substring(0, dotIndex);
        }
        normalized = normalized
                .replaceAll("\\d+", "")
                .replaceAll("[^a-z\\p{IsHan}]", "");
        if (normalized.length() > 18) {
            return normalized.substring(0, 18);
        }
        return normalized;
    }

    private String resolveCategoryKey(Product product) {
        if (product == null) {
            return "category:unknown";
        }
        if (product.getCategoryId() != null) {
            return "category:id:" + product.getCategoryId();
        }
        String categoryName = normalizeText(product.getCategoryName());
        if (!categoryName.isEmpty()) {
            return "category:name:" + categoryName;
        }
        Long productId = product.getId() == null ? 0L : product.getId();
        return "category:unknown:" + productId;
    }

    private String resolveMerchantKey(Product product) {
        if (product == null) {
            return "merchant:unknown";
        }
        if (product.getMerchantId() != null) {
            return "merchant:id:" + product.getMerchantId();
        }
        String merchantName = normalizeText(product.getMerchantName());
        if (!merchantName.isEmpty()) {
            return "merchant:name:" + merchantName;
        }
        Long productId = product.getId() == null ? 0L : product.getId();
        return "merchant:unknown:" + productId;
    }

    private Map<String, Object> buildDistributionQuality(Map<String, Object> summary,
                                                         List<Map<String, Object>> sceneMetrics,
                                                         List<Map<String, Object>> algorithmMetrics,
                                                         List<Map<String, Object>> segmentMetrics,
                                                         List<Map<String, Object>> reasonTypeMetrics) {
        long exposureCount = readLong(summary == null ? null : summary.get("exposureCount"));
        double sceneHhi = computeHhi(sceneMetrics);
        double algorithmHhi = computeHhi(algorithmMetrics);
        double segmentHhi = computeHhi(segmentMetrics);
        double concentrationIndex = (sceneHhi * 0.4 + algorithmHhi * 0.4 + segmentHhi * 0.2);
        double qualityScore = round2((1D - clamp(concentrationIndex, 0D, 1D)) * 100D);

        long sceneCount = sceneMetrics == null ? 0L : sceneMetrics.stream()
                .filter(Objects::nonNull)
                .map(item -> stringValue(item.get("scene")))
                .filter(StringUtils::hasText)
                .count();
        long algorithmCount = algorithmMetrics == null ? 0L : algorithmMetrics.stream()
                .filter(Objects::nonNull)
                .map(item -> stringValue(item.get("algorithm")))
                .filter(StringUtils::hasText)
                .count();
        long segmentCount = segmentMetrics == null ? 0L : segmentMetrics.stream()
                .filter(Objects::nonNull)
                .map(item -> stringValue(item.get("segmentCode")))
                .filter(StringUtils::hasText)
                .count();
        long reasonTypeCount = reasonTypeMetrics == null ? 0L : reasonTypeMetrics.stream()
                .filter(Objects::nonNull)
                .map(item -> stringValue(item.get("reasonType")))
                .filter(StringUtils::hasText)
                .count();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qualityScore", qualityScore);
        payload.put("level", resolveDistributionLevel(qualityScore));
        payload.put("sceneConcentrationHhi", round2(sceneHhi));
        payload.put("algorithmConcentrationHhi", round2(algorithmHhi));
        payload.put("segmentConcentrationHhi", round2(segmentHhi));
        payload.put("sceneCount", sceneCount);
        payload.put("algorithmCount", algorithmCount);
        payload.put("segmentCount", segmentCount);
        payload.put("reasonTypeCount", reasonTypeCount);
        payload.put("totalExposureCount", exposureCount);
        payload.put("insight", buildDistributionInsight(qualityScore, sceneCount, algorithmCount, segmentCount));
        return payload;
    }

    private List<Map<String, Object>> buildComparisonHighlights(List<Map<String, Object>> sceneMetrics,
                                                                List<Map<String, Object>> segmentMetrics,
                                                                List<Map<String, Object>> algorithmMetrics) {
        List<Map<String, Object>> highlights = new ArrayList<>();
        highlights.add(buildMetricComparison(
                "scene",
                "入口效果对比",
                sceneMetrics,
                row -> SCENE_HOT.equalsIgnoreCase(stringValue(row.get("scene"))),
                row -> resolveSceneLabel(stringValue(row.get("scene"))),
                "用热门候选作为基线，直接说明不同入口是否真的优于统一推荐。"));
        highlights.add(buildMetricComparison(
                "algorithm",
                "算法效果对比",
                algorithmMetrics,
                this::isBaselineAlgorithmMetric,
                row -> resolveAlgorithmLabel(stringValue(row.get("algorithm"))),
                "突出混合推荐、多目标排序是否比基线算法更有效。"));
        highlights.add(buildMetricComparison(
                "segment",
                "人群效果对比",
                segmentMetrics,
                row -> "COLD_START".equalsIgnoreCase(stringValue(row.get("segmentCode"))),
                this::resolveSegmentLabel,
                "用冷启动人群作为基线，验证分群策略的真实业务价值。"));
        return highlights;
    }

    private Map<String, Object> buildMetricComparison(String dimension,
                                                      String title,
                                                      List<Map<String, Object>> metrics,
                                                      Predicate<Map<String, Object>> baselineMatcher,
                                                      Function<Map<String, Object>, String> labelResolver,
                                                      String note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dimension", dimension);
        payload.put("title", title);
        payload.put("note", note);
        if (metrics == null || metrics.isEmpty()) {
            payload.put("available", false);
            payload.put("summary", "当前周期暂无可比较数据。");
            return payload;
        }

        Map<String, Object> winner = pickBestMetricRow(metrics);
        Map<String, Object> baseline = pickBaselineMetricRow(metrics, baselineMatcher);
        if (winner == null) {
            payload.put("available", false);
            payload.put("summary", "当前周期暂无可比较数据。");
            return payload;
        }
        if (baseline == null) {
            baseline = winner;
        }

        String winnerLabel = labelResolver.apply(winner);
        String baselineLabel = labelResolver.apply(baseline);
        double winnerConversionRate = parseDouble(winner.get("conversionRate"));
        double baselineConversionRate = parseDouble(baseline.get("conversionRate"));
        double winnerSuccessRate = parseDouble(winner.get("recommendationSuccessRate"));
        double baselineSuccessRate = parseDouble(baseline.get("recommendationSuccessRate"));
        Double conversionLiftRatio = relativeLift(winnerConversionRate, baselineConversionRate);
        Double successLiftRatio = relativeLift(winnerSuccessRate, baselineSuccessRate);

        payload.put("available", true);
        payload.put("winnerLabel", winnerLabel);
        payload.put("baselineLabel", baselineLabel);
        payload.put("winnerConversionRate", round2(winnerConversionRate));
        payload.put("baselineConversionRate", round2(baselineConversionRate));
        payload.put("winnerSuccessRate", round2(winnerSuccessRate));
        payload.put("baselineSuccessRate", round2(baselineSuccessRate));
        payload.put("winnerExposureCount", readLong(winner.get("exposureCount")));
        payload.put("baselineExposureCount", readLong(baseline.get("exposureCount")));
        payload.put("conversionLiftRatio", conversionLiftRatio);
        payload.put("successLiftRatio", successLiftRatio);
        payload.put("summary", buildComparisonSummaryText(
                winnerLabel,
                baselineLabel,
                winnerConversionRate,
                baselineConversionRate,
                conversionLiftRatio,
                successLiftRatio));

        if (winner.containsKey("scene")) {
            payload.put("scene", winner.get("scene"));
        }
        if (winner.containsKey("algorithm")) {
            payload.put("algorithm", winner.get("algorithm"));
        }
        if (winner.containsKey("segmentCode")) {
            payload.put("segmentCode", winner.get("segmentCode"));
        }
        if (winner.containsKey("segmentName")) {
            payload.put("segmentName", winner.get("segmentName"));
        }
        return payload;
    }

    private Map<String, Object> pickBestMetricRow(List<Map<String, Object>> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return null;
        }
        return metrics.stream()
                .filter(Objects::nonNull)
                .sorted((left, right) -> {
                    int compareConversion = Double.compare(
                            parseDouble(right.get("conversionRate")),
                            parseDouble(left.get("conversionRate")));
                    if (compareConversion != 0) {
                        return compareConversion;
                    }
                    int compareSuccess = Double.compare(
                            parseDouble(right.get("recommendationSuccessRate")),
                            parseDouble(left.get("recommendationSuccessRate")));
                    if (compareSuccess != 0) {
                        return compareSuccess;
                    }
                    return Long.compare(
                            readLong(right.get("exposureCount")),
                            readLong(left.get("exposureCount")));
                })
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> pickBaselineMetricRow(List<Map<String, Object>> metrics,
                                                      Predicate<Map<String, Object>> matcher) {
        if (metrics == null || metrics.isEmpty()) {
            return null;
        }

        if (matcher != null) {
            for (Map<String, Object> metric : metrics) {
                if (metric != null && matcher.test(metric)) {
                    return metric;
                }
            }
        }

        return metrics.stream()
                .filter(Objects::nonNull)
                .sorted((left, right) -> {
                    int compareConversion = Double.compare(
                            parseDouble(left.get("conversionRate")),
                            parseDouble(right.get("conversionRate")));
                    if (compareConversion != 0) {
                        return compareConversion;
                    }
                    return Long.compare(
                            readLong(left.get("exposureCount")),
                            readLong(right.get("exposureCount")));
                })
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildBestAlgorithmSegment(List<Map<String, Object>> algorithmSegmentMetrics) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (algorithmSegmentMetrics == null || algorithmSegmentMetrics.isEmpty()) {
            payload.put("available", false);
            payload.put("summary", "当前周期暂无算法与人群组合数据。");
            return payload;
        }

        Map<String, Object> winner = pickBestMetricRow(algorithmSegmentMetrics);
        if (winner == null) {
            payload.put("available", false);
            payload.put("summary", "当前周期暂无算法与人群组合数据。");
            return payload;
        }

        String segmentLabel = resolveSegmentLabel(winner);
        String algorithmLabel = resolveAlgorithmLabel(stringValue(winner.get("algorithm")));
        payload.put("available", true);
        payload.put("segmentLabel", segmentLabel);
        payload.put("algorithmLabel", algorithmLabel);
        payload.put("conversionRate", round2(parseDouble(winner.get("conversionRate"))));
        payload.put("successRate", round2(parseDouble(winner.get("recommendationSuccessRate"))));
        payload.put("exposureCount", readLong(winner.get("exposureCount")));
        payload.put("summary", segmentLabel + " 更适合由 " + algorithmLabel + " 承接，成交转化率 "
                + formatPercentValue(parseDouble(winner.get("conversionRate")))
                + "，推荐成功率 "
                + formatPercentValue(parseDouble(winner.get("recommendationSuccessRate"))) + "。");
        return payload;
    }

    private List<Map<String, Object>> buildSceneAlgorithmLeaders(List<Map<String, Object>> sceneAlgorithmMetrics) {
        if (sceneAlgorithmMetrics == null || sceneAlgorithmMetrics.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<Map<String, Object>>> grouped = sceneAlgorithmMetrics.stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.hasText(stringValue(item.get("scene"))))
                .collect(Collectors.groupingBy(
                        item -> stringValue(item.get("scene")),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Map<String, Object>> leaders = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            Map<String, Object> winner = pickBestMetricRow(entry.getValue());
            if (winner == null) {
                continue;
            }
            long sceneExposure = entry.getValue().stream()
                    .mapToLong(item -> readLong(item.get("exposureCount")))
                    .sum();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scene", entry.getKey());
            payload.put("sceneLabel", resolveSceneLabel(entry.getKey()));
            payload.put("algorithm", winner.get("algorithm"));
            payload.put("algorithmLabel", resolveAlgorithmLabel(stringValue(winner.get("algorithm"))));
            payload.put("exposureCount", readLong(winner.get("exposureCount")));
            payload.put("shareInsideScene", ratioPercent(readLong(winner.get("exposureCount")), sceneExposure));
            payload.put("conversionRate", round2(parseDouble(winner.get("conversionRate"))));
            payload.put("successRate", round2(parseDouble(winner.get("recommendationSuccessRate"))));
            payload.put("summary", resolveSceneLabel(entry.getKey()) + " 当前由 "
                    + resolveAlgorithmLabel(stringValue(winner.get("algorithm")))
                    + " 表现最好，成交转化率 "
                    + formatPercentValue(parseDouble(winner.get("conversionRate"))) + "。");
            leaders.add(payload);
        }

        leaders.sort((left, right) -> Double.compare(
                parseDouble(right.get("conversionRate")),
                parseDouble(left.get("conversionRate"))));
        return leaders;
    }

    private List<Map<String, Object>> buildOptimizationStages(Map<String, Object> summary,
                                                              List<Map<String, Object>> sourceTypeMetrics,
                                                              List<Map<String, Object>> reasonTypeMetrics,
                                                              List<Map<String, Object>> algorithmMetrics,
                                                              Map<String, Object> distributionQuality,
                                                              Map<String, Object> sevenDayRepurchase) {
        long sourceTypeCount = countDistinctMetrics(sourceTypeMetrics, "sourceType");
        long reasonTypeCount = countDistinctMetrics(reasonTypeMetrics, "reasonType");
        Map<String, Object> bestAlgorithm = pickBestMetricRow(algorithmMetrics);

        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(buildOptimizationStage(
                "recall",
                "召回层",
                "CF / 内容标签 / 热门候选 / 分群补全",
                "扩大候选覆盖",
                "当前候选来源 " + sourceTypeCount + " 类，解释信号 " + reasonTypeCount + " 类。",
                "覆盖用户 " + formatNumberValue(readLong(summary.get("userCount")))
                        + "，点击率 " + formatPercentValue(parseDouble(summary.get("clickThroughRate"))) + "。",
                "避免把推荐页做成热门榜单复刻。"));
        stages.add(buildOptimizationStage(
                "rank",
                "排序层",
                "CTR + CVR + GMV + 退款惩罚",
                "改善成交质量",
                bestAlgorithm == null
                        ? "当前周期暂无算法对比结果。"
                        : "当前最优算法为 " + resolveAlgorithmLabel(stringValue(bestAlgorithm.get("algorithm"))) + "。",
                "点击后转化 " + formatPercentValue(parseDouble(summary.get("postClickConversionRate")))
                        + "，成交转化 " + formatPercentValue(parseDouble(summary.get("conversionRate"))) + "。",
                "比赛展示时强调：排序不是只看 CTR，而是看成交和商业质量。"));
        stages.add(buildOptimizationStage(
                "rerank",
                "重排层",
                "多样性 / 去重 / 单类目上限 / 单商家上限",
                "控制同质化",
                "流量分布健康度 " + formatPercentValue(parseDouble(distributionQuality.get("qualityScore")))
                        + "，覆盖场景 " + readLong(distributionQuality.get("sceneCount"))
                        + " 类、分群 " + readLong(distributionQuality.get("segmentCount")) + " 类。",
                String.valueOf(distributionQuality.getOrDefault("insight", "当前分布较均衡。")),
                "适合解释为什么推荐列表更像抖音商城，而不是重复推同类商品。"));
        stages.add(buildOptimizationStage(
                "governance",
                "评估层",
                "A/B 实验 / 7日复购 / 自动回滚",
                "形成闭环验证",
                "推荐成功率 " + formatPercentValue(parseDouble(summary.get("recommendationSuccessRate")))
                        + "，7日复购率 " + formatPercentValue(parseDouble(sevenDayRepurchase.get("sevenDayRepurchaseRate"))) + "。",
                "归因首购 " + formatNumberValue(readLong(sevenDayRepurchase.get("attributedPurchaseUsers")))
                        + " 人，复购 " + formatNumberValue(readLong(sevenDayRepurchase.get("repurchaseUsers"))) + " 人。",
                "让评委看到系统具备“上线验证”和“效果复盘”能力。"));
        return stages;
    }

    private Map<String, Object> buildOptimizationStage(String key,
                                                       String title,
                                                       String techStack,
                                                       String focus,
                                                       String evidence,
                                                       String result,
                                                       String note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", key);
        payload.put("title", title);
        payload.put("techStack", techStack);
        payload.put("focus", focus);
        payload.put("evidence", evidence);
        payload.put("result", result);
        payload.put("note", note);
        return payload;
    }

    private List<Map<String, Object>> buildDiagnosticCards(Map<String, Object> summary,
                                                           Map<String, Object> distributionQuality,
                                                           Map<String, Object> sevenDayRepurchase,
                                                           List<Map<String, Object>> comparisonHighlights,
                                                           Map<String, Object> bestAlgorithmSegment) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(buildDiagnosticCard(
                "点击后成交效率",
                formatPercentValue(parseDouble(summary.get("postClickConversionRate"))),
                resolveRateTone(parseDouble(summary.get("postClickConversionRate")), 10D, 5D),
                "衡量用户点击推荐商品后最终下单的效率。",
                "当前成交效率适合作为“排序层优化是否有效”的核心证据。"));
        cards.add(buildDiagnosticCard(
                "7日复购粘性",
                formatPercentValue(parseDouble(sevenDayRepurchase.get("sevenDayRepurchaseRate"))),
                resolveRateTone(parseDouble(sevenDayRepurchase.get("sevenDayRepurchaseRate")), 18D, 8D),
                "衡量推荐带来首购之后，用户是否愿意继续回来买。",
                "比赛展示建议把复购率和成交转化率一起讲，证明不是一次性点击。"));
        cards.add(buildDiagnosticCard(
                "流量分布健康度",
                formatPercentValue(parseDouble(distributionQuality.get("qualityScore"))),
                resolveRateTone(parseDouble(distributionQuality.get("qualityScore")), 82D, 68D),
                "综合场景、算法、人群的集中度，判断推荐是否过于单一。",
                stringValue(distributionQuality.get("insight"))));

        Map<String, Object> algorithmHighlight = comparisonHighlights == null ? null : comparisonHighlights.stream()
                .filter(Objects::nonNull)
                .filter(item -> "algorithm".equals(stringValue(item.get("dimension"))))
                .findFirst()
                .orElse(null);
        String algorithmValue = algorithmHighlight == null
                ? "暂无"
                : stringValue(algorithmHighlight.get("winnerLabel"));
        String algorithmDetail = algorithmHighlight == null
                ? "当前周期暂无算法对比结论。"
                : stringValue(algorithmHighlight.get("summary"));
        if (Boolean.TRUE.equals(bestAlgorithmSegment.get("available"))) {
            algorithmDetail = algorithmDetail + " " + stringValue(bestAlgorithmSegment.get("summary"));
        }
        cards.add(buildDiagnosticCard(
                "当前最佳承接策略",
                algorithmValue,
                "info",
                "直接回答“现在最值得展示的算法策略是什么”。",
                algorithmDetail));
        return cards;
    }

    private Map<String, Object> buildDiagnosticCard(String title,
                                                    String value,
                                                    String tone,
                                                    String summary,
                                                    String detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("value", value);
        payload.put("tone", tone);
        payload.put("summary", summary);
        payload.put("detail", detail);
        return payload;
    }

    private List<String> buildDefenseNarrative(Map<String, Object> summary,
                                               Map<String, Object> distributionQuality,
                                               Map<String, Object> sevenDayRepurchase,
                                               List<Map<String, Object>> comparisonHighlights,
                                               Map<String, Object> bestAlgorithmSegment) {
        List<String> lines = new ArrayList<>();
        lines.add("我们不是只看曝光和点击，而是同时跟踪成交转化、7日复购和流量分布健康度。");
        if (comparisonHighlights != null) {
            comparisonHighlights.stream()
                    .filter(Objects::nonNull)
                    .map(item -> stringValue(item.get("summary")))
                    .filter(StringUtils::hasText)
                    .limit(2)
                    .forEach(lines::add);
        }
        if (Boolean.TRUE.equals(bestAlgorithmSegment.get("available"))) {
            lines.add(stringValue(bestAlgorithmSegment.get("summary")));
        }
        lines.add("当前推荐成功率为 "
                + formatPercentValue(parseDouble(summary.get("recommendationSuccessRate")))
                + "，7日复购率为 "
                + formatPercentValue(parseDouble(sevenDayRepurchase.get("sevenDayRepurchaseRate")))
                + "，说明系统不只会推，还能带来持续购买。");
        lines.add("流量分布健康度为 "
                + formatPercentValue(parseDouble(distributionQuality.get("qualityScore")))
                + "，可用于解释为什么系统在关注成交的同时仍然控制了同质化风险。");
        return lines;
    }

    private Double relativeLift(double current, double baseline) {
        if (baseline <= 0D) {
            return null;
        }
        return round2((current - baseline) / baseline);
    }

    private String buildComparisonSummaryText(String winnerLabel,
                                              String baselineLabel,
                                              double winnerConversionRate,
                                              double baselineConversionRate,
                                              Double conversionLiftRatio,
                                              Double successLiftRatio) {
        StringBuilder builder = new StringBuilder();
        builder.append(winnerLabel)
                .append(" 相对 ")
                .append(baselineLabel)
                .append("，成交转化率从 ")
                .append(formatPercentValue(baselineConversionRate))
                .append(" 到 ")
                .append(formatPercentValue(winnerConversionRate));
        if (conversionLiftRatio != null) {
            builder.append("，相对差异 ").append(formatLiftRatio(conversionLiftRatio));
        }
        if (successLiftRatio != null) {
            builder.append("，推荐成功率差异 ").append(formatLiftRatio(successLiftRatio));
        }
        builder.append("。");
        return builder.toString();
    }

    private long countDistinctMetrics(List<Map<String, Object>> metrics, String key) {
        if (metrics == null || metrics.isEmpty()) {
            return 0L;
        }
        return metrics.stream()
                .filter(Objects::nonNull)
                .map(item -> stringValue(item.get(key)))
                .filter(StringUtils::hasText)
                .distinct()
                .count();
    }

    private boolean isBaselineAlgorithmMetric(Map<String, Object> row) {
        String algorithm = stringValue(row == null ? null : row.get("algorithm"));
        if (!StringUtils.hasText(algorithm)) {
            return false;
        }
        String normalized = algorithm.toLowerCase(Locale.ROOT);
        return normalized.contains("hot")
                || normalized.contains("snapshot")
                || normalized.contains("control");
    }

    private String resolveSceneLabel(String scene) {
        if (!StringUtils.hasText(scene)) {
            return "未知场景";
        }
        switch (scene) {
            case SCENE_PERSONAL:
                return "个性化推荐";
            case SCENE_GUESS_YOU_LIKE:
                return "猜你喜欢";
            case SCENE_HOT:
                return "热门候选";
            case SCENE_SIMILAR:
                return "相似商品";
            default:
                return scene;
        }
    }

    private String resolveAlgorithmLabel(String algorithm) {
        if (!StringUtils.hasText(algorithm)) {
            return "未知算法";
        }
        String normalized = algorithm.toLowerCase(Locale.ROOT);
        if (normalized.contains("hybrid")) {
            return "Hybrid 混合推荐";
        }
        if (normalized.equals("cf") || normalized.contains("collaborative") || normalized.contains("_cf")) {
            return "CF 协同过滤";
        }
        if (normalized.equals("cb") || normalized.contains("content")) {
            return "CB 内容推荐";
        }
        if (normalized.contains("hot") || normalized.contains("snapshot")) {
            return "热门候选";
        }
        if (normalized.contains("control")) {
            return "对照策略";
        }
        return algorithm;
    }

    private String resolveSegmentLabel(Map<String, Object> row) {
        if (row == null) {
            return "未命名人群";
        }
        String segmentName = stringValue(row.get("segmentName"));
        if (StringUtils.hasText(segmentName)) {
            return segmentName;
        }
        String segmentCode = stringValue(row.get("segmentCode"));
        return StringUtils.hasText(segmentCode) ? segmentCode : "未命名人群";
    }

    private String resolveRateTone(double value, double goodThreshold, double fairThreshold) {
        if (value >= goodThreshold) {
            return "success";
        }
        if (value >= fairThreshold) {
            return "warning";
        }
        return "danger";
    }

    private String formatPercentValue(double value) {
        return round2(value) + "%";
    }

    private String formatLiftRatio(Double value) {
        if (value == null) {
            return "--";
        }
        double display = round2(value * 100D);
        return (display >= 0D ? "+" : "") + display + "%";
    }

    private String formatNumberValue(long value) {
        return String.format(Locale.CHINA, "%,d", value);
    }

    private double computeHhi(List<Map<String, Object>> metricRows) {
        if (metricRows == null || metricRows.isEmpty()) {
            return 0D;
        }
        long totalExposure = metricRows.stream()
                .mapToLong(row -> readLong(row == null ? null : row.get("exposureCount")))
                .sum();
        if (totalExposure <= 0L) {
            return 0D;
        }

        double hhi = 0D;
        for (Map<String, Object> row : metricRows) {
            long rowExposure = readLong(row == null ? null : row.get("exposureCount"));
            if (rowExposure <= 0L) {
                continue;
            }
            double share = (double) rowExposure / (double) totalExposure;
            hhi += share * share;
        }
        return hhi;
    }

    private String resolveDistributionLevel(double qualityScore) {
        if (qualityScore >= 88D) {
            return "excellent";
        }
        if (qualityScore >= 76D) {
            return "good";
        }
        if (qualityScore >= 62D) {
            return "fair";
        }
        return "concentrated";
    }

    private String buildDistributionInsight(double qualityScore,
                                            long sceneCount,
                                            long algorithmCount,
                                            long segmentCount) {
        if (qualityScore >= 88D) {
            return "流量分布较均衡，探索与转化策略平衡良好。";
        }
        if (qualityScore >= 76D) {
            return "分布整体健康，可继续优化长尾场景和低覆盖人群。";
        }
        if (sceneCount <= 1 || algorithmCount <= 1 || segmentCount <= 1) {
            return "分布集中度偏高，建议增加多路召回和分群差异化策略。";
        }
        return "推荐流量存在头部集中，建议小幅增加探索配额并监控回落风险。";
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Map<String, Object> buildMetricBlock(Map<String, Object> rawMetric) {
        Map<String, Object> result = new LinkedHashMap<>();
        long exposureCount = readLong(rawMetric == null ? null : rawMetric.get("exposureCount"));
        long userCount = readLong(rawMetric == null ? null : rawMetric.get("userCount"));
        long clickCount = readLong(rawMetric == null ? null : rawMetric.get("clickCount"));
        long favoriteCount = readLong(rawMetric == null ? null : rawMetric.get("favoriteCount"));
        long cartCount = readLong(rawMetric == null ? null : rawMetric.get("cartCount"));
        long purchaseCount = readLong(rawMetric == null ? null : rawMetric.get("purchaseCount"));
        long successCount = readLong(rawMetric == null ? null : rawMetric.get("successCount"));

        result.put("exposureCount", exposureCount);
        result.put("userCount", userCount);
        result.put("clickCount", clickCount);
        result.put("favoriteCount", favoriteCount);
        result.put("cartCount", cartCount);
        result.put("purchaseCount", purchaseCount);
        result.put("successCount", successCount);
        result.put("clickThroughRate", ratioPercent(clickCount, exposureCount));
        result.put("favoriteRate", ratioPercent(favoriteCount, exposureCount));
        result.put("addToCartRate", ratioPercent(cartCount, exposureCount));
        result.put("conversionRate", ratioPercent(purchaseCount, exposureCount));
        result.put("recommendationSuccessRate", ratioPercent(successCount, exposureCount));
        result.put("postClickConversionRate", ratioPercent(purchaseCount, clickCount));

        if (rawMetric != null) {
            Object scene = rawMetric.get("scene");
            Object segmentCode = rawMetric.get("segmentCode");
            Object segmentName = rawMetric.get("segmentName");
            Object algorithm = rawMetric.get("algorithm");
            Object sourceType = rawMetric.get("sourceType");
            Object reasonType = rawMetric.get("reasonType");
            Object modelVersion = rawMetric.get("modelVersion");
            Object statDate = rawMetric.get("statDate");
            if (scene != null) {
                result.put("scene", scene);
            }
            if (segmentCode != null) {
                result.put("segmentCode", segmentCode);
            }
            if (segmentName != null) {
                result.put("segmentName", segmentName);
            }
            if (algorithm != null) {
                result.put("algorithm", algorithm);
            }
            if (sourceType != null) {
                result.put("sourceType", sourceType);
            }
            if (reasonType != null) {
                result.put("reasonType", reasonType);
            }
            if (modelVersion != null) {
                result.put("modelVersion", modelVersion);
            }
            if (statDate != null) {
                result.put("statDate", statDate);
            }
        }
        return result;
    }

    private List<Map<String, Object>> buildMetricList(List<Map<String, Object>> rawMetrics) {
        if (rawMetrics == null || rawMetrics.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> rawMetric : rawMetrics) {
            result.add(buildMetricBlock(rawMetric));
        }
        return result;
    }

    private List<Map<String, Object>> loadOptionalMetricList(String metricName,
                                                             Supplier<List<Map<String, Object>>> supplier) {
        try {
            return buildMetricList(supplier.get());
        } catch (Exception exception) {
            log.warn("[RecommendationMetrics] metric {} unavailable, fallback empty: {}",
                    metricName, exception.getMessage());
            return Collections.emptyList();
        }
    }

    private long readLong(Object rawValue) {
        if (rawValue == null) {
            return 0L;
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(rawValue));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private double ratioPercent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf((double) numerator * 100.0 / denominator)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String normalizeRecommendationEventType(String rawEventType) {
        if (!StringUtils.hasText(rawEventType)) {
            return null;
        }
        String normalized = rawEventType.trim().toLowerCase(Locale.ROOT);
        if (Constants.RecommendationEventType.EXPOSURE.equals(normalized)
                || Constants.RecommendationEventType.CLICK.equals(normalized)
                || Constants.RecommendationEventType.DWELL.equals(normalized)
                || Constants.RecommendationEventType.ADD_CART.equals(normalized)
                || Constants.RecommendationEventType.ORDER.equals(normalized)
                || Constants.RecommendationEventType.REFUND.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String toBehaviorType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return null;
        }
        if (Constants.RecommendationEventType.CLICK.equals(eventType)
                || Constants.RecommendationEventType.DWELL.equals(eventType)) {
            return Constants.BehaviorType.VIEW;
        }
        if (Constants.RecommendationEventType.ADD_CART.equals(eventType)) {
            return Constants.BehaviorType.CART;
        }
        if (Constants.RecommendationEventType.ORDER.equals(eventType)) {
            return Constants.BehaviorType.PURCHASE;
        }
        return null;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> T firstOrNull(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private static class LiveRecommendationDecision {
        private final List<Product> products;
        private final String algorithmTag;

        private LiveRecommendationDecision(List<Product> products, String algorithmTag) {
            this.products = products == null ? Collections.emptyList() : products;
            this.algorithmTag = StringUtils.hasText(algorithmTag)
                    ? algorithmTag
                    : ALGO_HYBRID_LIVE_NO_CF;
        }
    }

    private static class ClusterContext {
        private String segmentCode;
        private String segmentName;
        private List<String> topCategories = Collections.emptyList();
        private List<String> topTags = Collections.emptyList();
        private BigDecimal avgPricePerOrder;
        private boolean coldStart;

        private boolean hasCategorySignal() {
            return topCategories != null && !topCategories.isEmpty();
        }

        private boolean hasTagSignal() {
            return topTags != null && !topTags.isEmpty();
        }
    }

    private static class SessionExposureContext {
        private final Map<Long, Integer> productExposureCounter;
        private final Map<String, Integer> categoryExposureCounter;
        private final Map<String, Integer> merchantExposureCounter;

        private SessionExposureContext(Map<Long, Integer> productExposureCounter,
                                       Map<String, Integer> categoryExposureCounter,
                                       Map<String, Integer> merchantExposureCounter) {
            this.productExposureCounter = productExposureCounter == null ? Collections.emptyMap() : productExposureCounter;
            this.categoryExposureCounter = categoryExposureCounter == null ? Collections.emptyMap() : categoryExposureCounter;
            this.merchantExposureCounter = merchantExposureCounter == null ? Collections.emptyMap() : merchantExposureCounter;
        }

        private boolean isEmpty() {
            return productExposureCounter.isEmpty()
                    && categoryExposureCounter.isEmpty()
                    && merchantExposureCounter.isEmpty();
        }

        private static SessionExposureContext empty() {
            return new SessionExposureContext(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }

    private static class SceneGuardrailConfig {
        private final int maxPerCategory;
        private final int maxPerMerchant;
        private final int maxPerNearDuplicate;
        private final int strictWindowSize;
        private final int supplementMultiplier;

        private SceneGuardrailConfig(int maxPerCategory,
                                     int maxPerMerchant,
                                     int maxPerNearDuplicate,
                                     int strictWindowSize,
                                     int supplementMultiplier) {
            this.maxPerCategory = maxPerCategory;
            this.maxPerMerchant = maxPerMerchant;
            this.maxPerNearDuplicate = maxPerNearDuplicate;
            this.strictWindowSize = strictWindowSize;
            this.supplementMultiplier = supplementMultiplier;
        }
    }

    private static class NegativeFeedbackContext {
        private final Map<Long, Integer> productPenaltyCounter;
        private final Map<String, Integer> categoryPenaltyCounter;
        private final Set<Long> dislikedProductIds;

        private NegativeFeedbackContext(Map<Long, Integer> productPenaltyCounter,
                                        Map<String, Integer> categoryPenaltyCounter,
                                        Set<Long> dislikedProductIds) {
            this.productPenaltyCounter = productPenaltyCounter == null ? Collections.emptyMap() : productPenaltyCounter;
            this.categoryPenaltyCounter = categoryPenaltyCounter == null ? Collections.emptyMap() : categoryPenaltyCounter;
            this.dislikedProductIds = dislikedProductIds == null ? Collections.emptySet() : dislikedProductIds;
        }

        private boolean isEmpty() {
            return productPenaltyCounter.isEmpty() && categoryPenaltyCounter.isEmpty() && dislikedProductIds.isEmpty();
        }

        private static NegativeFeedbackContext empty() {
            return new NegativeFeedbackContext(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet());
        }
    }

    private static class QualityGateAccumulator {
        private int orderCount;
        private int refundCount;
        private double gmvAmount;
    }

    private static class QualityGateSnapshot {
        private final int orderCount;
        private final int refundCount;
        private final double refundRate;
        private final double gmvAmount;
        private final double rating;
        private final int stock;
        private final boolean highRefundRisk;
        private final boolean lowRatingRisk;
        private final boolean lowStockRisk;

        private QualityGateSnapshot(int orderCount,
                                    int refundCount,
                                    double refundRate,
                                    double gmvAmount,
                                    double rating,
                                    int stock,
                                    boolean highRefundRisk,
                                    boolean lowRatingRisk,
                                    boolean lowStockRisk) {
            this.orderCount = orderCount;
            this.refundCount = refundCount;
            this.refundRate = refundRate;
            this.gmvAmount = gmvAmount;
            this.rating = rating;
            this.stock = stock;
            this.highRefundRisk = highRefundRisk;
            this.lowRatingRisk = lowRatingRisk;
            this.lowStockRisk = lowStockRisk;
        }
    }

    private static class SessionIntentContext {
        private final String intentType;
        private final double confidence;
        private final double behaviorEntropy;
        private final boolean newUser;
        private final List<String> searchTerms;

        private SessionIntentContext(String intentType,
                                     double confidence,
                                     double behaviorEntropy,
                                     boolean newUser,
                                     List<String> searchTerms) {
            this.intentType = intentType;
            this.confidence = confidence;
            this.behaviorEntropy = behaviorEntropy;
            this.newUser = newUser;
            this.searchTerms = searchTerms == null ? Collections.emptyList() : searchTerms;
        }

        private static SessionIntentContext browseDefault() {
            return new SessionIntentContext(SESSION_INTENT_BROWSE, 0D, 0D, true, Collections.emptyList());
        }
    }

    private static class ExposureFatigueScore {
        private final Product product;
        private final int originalIndex;
        private final double score;
        private final int exposureCount;
        private final int categoryExposureCount;
        private final double exploreScore;

        private ExposureFatigueScore(Product product,
                                     int originalIndex,
                                     double score,
                                     int exposureCount,
                                     int categoryExposureCount,
                                     double exploreScore) {
            this.product = product;
            this.originalIndex = originalIndex;
            this.score = score;
            this.exposureCount = exposureCount;
            this.categoryExposureCount = categoryExposureCount;
            this.exploreScore = exploreScore;
        }
    }

    private static class DistributionMetric {
        private final Map<String, Integer> categoryCounter;
        private final double normalizedEntropy;
        private final double maxCategoryRatio;
        private final String dominantCategoryKey;

        private DistributionMetric(Map<String, Integer> categoryCounter,
                                   double normalizedEntropy,
                                   double maxCategoryRatio,
                                   String dominantCategoryKey) {
            this.categoryCounter = categoryCounter;
            this.normalizedEntropy = normalizedEntropy;
            this.maxCategoryRatio = maxCategoryRatio;
            this.dominantCategoryKey = dominantCategoryKey;
        }
    }

    private static class ProductObjectiveMetrics {
        private int exposureCount;
        private int clickCount;
        private int purchaseCount;
    }

    private static class ObjectiveWeightBundle {
        private final double ctrWeight;
        private final double cvrWeight;
        private final double gmvWeight;
        private final double diversityWeight;
        private final double refundPenaltyWeight;
        private final double totalWeight;

        private ObjectiveWeightBundle(double ctrWeight,
                                      double cvrWeight,
                                      double gmvWeight,
                                      double diversityWeight,
                                      double refundPenaltyWeight,
                                      double totalWeight) {
            this.ctrWeight = ctrWeight;
            this.cvrWeight = cvrWeight;
            this.gmvWeight = gmvWeight;
            this.diversityWeight = diversityWeight;
            this.refundPenaltyWeight = refundPenaltyWeight;
            this.totalWeight = totalWeight;
        }
    }

    private static class ReasonPayload {
        private final String reasonType;
        private final String reasonText;
        private final List<String> matchedTags;

        private ReasonPayload(String reasonType, String reasonText) {
            this(reasonType, reasonText, Collections.emptyList());
        }

        private ReasonPayload(String reasonType, String reasonText, List<String> matchedTags) {
            this.reasonType = reasonType;
            this.reasonText = reasonText;
            this.matchedTags = matchedTags == null ? Collections.emptyList() : matchedTags;
        }
    }
}
