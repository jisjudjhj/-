package com.ecommerce.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InterestCommerceService {

    private static final Logger log = LoggerFactory.getLogger(InterestCommerceService.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private static final long GLOBAL_CATEGORY_SCORE_CACHE_MILLIS = 5 * 60 * 1000L;
    private volatile long globalCategoryScoreCacheTime = 0L;
    private volatile Map<String, Double> globalCategoryScoreCache = new LinkedHashMap<>();

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("电脑办公", List.of("电脑办公", "轻办公设备", "商务办公", "显示设备", "办公电脑", "办公键鼠", "显示器", "键鼠", "鼠标", "键盘", "打印机", "扩展坞", "双屏", "高刷", "轻办公"));
        CATEGORY_KEYWORDS.put("图书文具", List.of("图书文具", "学习文具", "书写文创", "文创图书", "办公文具", "图书", "文具", "文创", "笔记", "阅读", "文学", "小说", "钢笔", "中性笔"));
        CATEGORY_KEYWORDS.put("食品生鲜", List.of("食品生鲜", "生鲜礼盒", "饮品零食", "食品", "生鲜", "饮品", "水果", "蔬菜", "零食", "咖啡", "茶", "牛奶", "矿泉水", "肉脯", "燕麦"));
        CATEGORY_KEYWORDS.put("手机数码", List.of("手机数码", "智能手机", "手机", "数码", "耳机", "相机", "手表", "智能手表", "备用机"));
        CATEGORY_KEYWORDS.put("家用电器", List.of("家用电器", "家电", "电器", "厨房电器", "厨电", "净化器", "洗衣机", "冰箱", "空调", "微波炉", "电饭煲", "台灯"));
        CATEGORY_KEYWORDS.put("美妆护肤", List.of("美妆护肤", "美妆", "护肤", "彩妆", "香水", "口红", "精华", "口腔护理", "口腔", "牙刷", "牙膏", "个护", "护理"));
        CATEGORY_KEYWORDS.put("服饰鞋包", List.of("服饰鞋包", "服饰", "衣", "鞋", "包", "穿搭", "羽绒服", "差旅收纳"));
        CATEGORY_KEYWORDS.put("运动户外", List.of("运动户外", "露营出游", "城市骑行", "运动", "户外", "露营", "健身", "骑行", "跑步", "羽毛球"));
        CATEGORY_KEYWORDS.put("母婴玩具", List.of("母婴玩具", "陪伴玩偶", "母婴", "奶瓶", "纸尿裤", "玩具", "儿童", "积木", "安抚"));
        CATEGORY_KEYWORDS.put("家居家装", List.of("家居家装", "智能安防", "家务清洁", "家居", "家装", "收纳", "窗帘", "书架", "门锁", "地毯"));
    }

    public Map<String, Object> buildRecommendation(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 60));
        UserInterestProfile profile = buildProfile(userId);
        List<Map<String, Object>> products = loadCandidateProducts(profile.topCategories);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("algorithm", "interest-commerce-hybrid-v1");
        result.put("generatedAt", LocalDateTime.now().toString());
        result.put("profile", profile.toMap());

        if (!profile.available || products.isEmpty()) {
            result.put("dataStatus", profile.available ? "缺少可推荐商品" : "缺少真实用户行为数据");
            result.put("recommendations", List.of());
            result.put("quality", quality(List.of(), profile));
            result.put("rule", "没有真实行为或商品数据时不生成虚构推荐。");
            return result;
        }

        List<Map<String, Object>> recommendations = rerank(products, profile, safeLimit);
        result.put("dataStatus", "可用");
        result.put("recommendations", recommendations);
        result.put("quality", quality(recommendations, profile));
        result.put("rule", "搜索代表人找货，浏览/收藏/加购/购买代表货找人后的反馈；Top2 偏好品类进入最终重排约束。");
        return result;
    }

    public Map<String, Object> auditRecommendation(Long userId) {
        Map<String, Object> result = buildRecommendation(userId, 20);
        @SuppressWarnings("unchecked")
        Map<String, Object> quality = (Map<String, Object>) result.get("quality");
        List<String> problems = new ArrayList<>();

        double topTwoCoverage = number(quality.get("topTwoCoverage"));
        double firstThreeCoverage = number(quality.get("firstThreeCoverage"));
        if ("可用".equals(result.get("dataStatus"))) {
            if (topTwoCoverage < 60) {
                problems.add("Top10 偏好品类覆盖率低于 60%，推荐结果会和用户画像脱节。");
            }
            if (firstThreeCoverage < 66.67) {
                problems.add("前 3 个商品没有优先命中用户 Top2 偏好品类。");
            }
        }

        result.put("problems", problems);
        result.put("auditConclusion", problems.isEmpty() ? "通过" : "需要调整");
        return result;
    }

    public Map<String, Object> buildSegments() {
        List<Map<String, Object>> userRows = queryList(
                "SELECT u.id AS userId, " +
                        "COALESCE(SUM(CASE WHEN ub.behavior_type IN ('view', 'browse') THEN 1 ELSE 0 END), 0) AS viewCount, " +
                        "COALESCE(SUM(CASE WHEN ub.behavior_type IN ('favorite', 'collect') THEN 1 ELSE 0 END), 0) AS favoriteCount, " +
                        "COALESCE(SUM(CASE WHEN ub.behavior_type IN ('cart', 'add_cart') THEN 1 ELSE 0 END), 0) AS cartCount, " +
                        "COALESCE((SELECT COUNT(*) FROM `order` o WHERE o.user_id = u.id AND o.status IN (1,2,3)), 0) AS orderCount, " +
                        "COALESCE((SELECT SUM(sh.search_count) FROM search_history sh WHERE sh.user_id = u.id), 0) AS searchCount, " +
                        "COALESCE((SELECT AVG(o.total_amount) FROM `order` o WHERE o.user_id = u.id AND o.status IN (1,2,3)), 0) AS avgOrderAmount, " +
                        "MAX(ub.create_time) AS lastActiveTime " +
                        "FROM `user` u LEFT JOIN user_behavior ub ON ub.user_id = u.id " +
                        "WHERE u.role = 'user' AND COALESCE(u.deleted, 0) = 0 " +
                        "GROUP BY u.id LIMIT 500"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "用户兴趣分群");
        result.put("dataStatus", userRows.isEmpty() ? "缺少真实用户行为数据" : "可用");
        result.put("rule", "只用真实浏览、搜索、收藏、加购、购买信号分群，不生成模拟用户。");

        Map<String, Segment> segments = new LinkedHashMap<>();
        segments.put("高意向未购买", new Segment("高意向未购买", "收藏或加购较多，但暂无支付", "发放限时券或到货提醒"));
        segments.put("复购用户", new Segment("复购用户", "累计支付订单大于等于 2", "推荐新品、会员权益或周期补货商品"));
        segments.put("价格敏感", new Segment("价格敏感", "加购较多但支付少，适合优惠券促转化", "发放门槛券并跟踪加购转化"));
        segments.put("新客待激活", new Segment("新客待激活", "有浏览但暂无加购和支付", "推荐低门槛热销品并收集兴趣"));
        segments.put("主动搜索用户", new Segment("主动搜索用户", "搜索较多但未成交，适合补充对应品类商品", "补充搜索词对应商品或优化搜索承接"));

        Map<String, Segment> operationalSegments = buildOperationalSegments(userRows);

        for (Map<String, Object> row : userRows) {
            int orderCount = intNumber(row.get("orderCount"));
            int favoriteCount = intNumber(row.get("favoriteCount"));
            int cartCount = intNumber(row.get("cartCount"));
            int viewCount = intNumber(row.get("viewCount"));
            int searchCount = intNumber(row.get("searchCount"));

            if (orderCount >= 2) segments.get("复购用户").add(row);
            if ((favoriteCount + cartCount) >= 2 && orderCount == 0) segments.get("高意向未购买").add(row);
            if (cartCount >= 2 && orderCount <= 1) segments.get("价格敏感").add(row);
            if (viewCount > 0 && cartCount == 0 && orderCount == 0) segments.get("新客待激活").add(row);
            if (searchCount >= 3 && orderCount == 0) segments.get("主动搜索用户").add(row);
            addOperationalSegment(row, operationalSegments);
        }

        List<Map<String, Object>> segmentList = new ArrayList<>();
        for (Segment segment : segments.values()) {
            segmentList.add(segment.toMap());
        }
        List<Map<String, Object>> operationalSegmentList = new ArrayList<>();
        for (Segment segment : operationalSegments.values()) {
            operationalSegmentList.add(segment.toMap());
        }
        result.put("segments", segmentList);
        result.put("operationalSegments", operationalSegmentList);
        result.put("suitability", segmentSuitability(userRows.size(), segments, operationalSegments));
        return result;
    }

    private Map<String, Segment> buildOperationalSegments(List<Map<String, Object>> userRows) {
        Map<String, Segment> operationalSegments = new LinkedHashMap<>();
        operationalSegments.put("高价值复购", new Segment("高价值复购", "累计支付订单大于等于 2，且客单价高于样本中位数", "推荐会员权益、高客单新品和专属服务"));
        operationalSegments.put("搜索驱动复购", new Segment("搜索驱动复购", "搜索次数高于样本中位数", "补足搜索词对应商品，并优化搜索结果排序"));
        operationalSegments.put("加购犹豫复购", new Segment("加购犹豫复购", "加购次数高于样本中位数，且加购次数高于支付订单数", "发券或库存提醒，推动购物车转化"));
        operationalSegments.put("收藏偏好明确", new Segment("收藏偏好明确", "收藏次数高于样本中位数", "围绕收藏品类推荐同款、替代款和降价提醒"));
        operationalSegments.put("浏览活跃复购", new Segment("浏览活跃复购", "浏览次数高于样本中位数", "用相似商品和新品承接持续浏览兴趣"));
        if (userRows == null || userRows.isEmpty()) {
            return operationalSegments;
        }
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("medianAvgOrderAmount", medianDouble(userRows, "avgOrderAmount"));
        thresholds.put("medianSearchCount", medianInt(userRows, "searchCount"));
        thresholds.put("medianCartCount", medianInt(userRows, "cartCount"));
        thresholds.put("medianFavoriteCount", medianInt(userRows, "favoriteCount"));
        thresholds.put("medianViewCount", medianInt(userRows, "viewCount"));
        for (Segment segment : operationalSegments.values()) {
            segment.extra.put("thresholds", thresholds);
        }
        return operationalSegments;
    }

    private void addOperationalSegment(Map<String, Object> row, Map<String, Segment> operationalSegments) {
        int orderCount = intNumber(row.get("orderCount"));
        int cartCount = intNumber(row.get("cartCount"));
        int favoriteCount = intNumber(row.get("favoriteCount"));
        int viewCount = intNumber(row.get("viewCount"));
        int searchCount = intNumber(row.get("searchCount"));
        double avgOrderAmount = number(row.get("avgOrderAmount"));

        Map<String, Object> thresholds = operationalSegments.values().stream()
                .findFirst()
                .map(segment -> segment.extra.get("thresholds"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .orElse(new LinkedHashMap<>());
        double medianAvgOrderAmount = number(thresholds.get("medianAvgOrderAmount"));
        int medianSearchCount = intNumber(thresholds.get("medianSearchCount"));
        int medianCartCount = intNumber(thresholds.get("medianCartCount"));
        int medianFavoriteCount = intNumber(thresholds.get("medianFavoriteCount"));
        int medianViewCount = intNumber(thresholds.get("medianViewCount"));

        if (orderCount >= 2 && avgOrderAmount >= medianAvgOrderAmount) {
            operationalSegments.get("高价值复购").add(row);
        }
        if (searchCount >= Math.max(3, medianSearchCount)) {
            operationalSegments.get("搜索驱动复购").add(row);
        }
        if (cartCount >= Math.max(2, medianCartCount) && cartCount > orderCount) {
            operationalSegments.get("加购犹豫复购").add(row);
        }
        if (favoriteCount >= Math.max(2, medianFavoriteCount)) {
            operationalSegments.get("收藏偏好明确").add(row);
        }
        if (viewCount >= Math.max(1, medianViewCount)) {
            operationalSegments.get("浏览活跃复购").add(row);
        }
    }

    private Map<String, Object> segmentSuitability(int totalUsers, Map<String, Segment> segments, Map<String, Segment> operationalSegments) {
        Map<String, Object> suitability = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int nonEmptySegments = 0;
        int nonEmptyOperationalSegments = 0;
        int maxCount = 0;
        String dominantSegment = "";
        for (Segment segment : segments.values()) {
            if (segment.count > 0) {
                nonEmptySegments++;
            }
            if (segment.count > maxCount) {
                maxCount = segment.count;
                dominantSegment = segment.name;
            }
        }
        for (Segment segment : operationalSegments.values()) {
            if (segment.count > 0) {
                nonEmptyOperationalSegments++;
            }
        }
        double dominantRatio = totalUsers <= 0 ? 0 : round(maxCount * 100.0 / totalUsers);
        if (totalUsers == 0) {
            warnings.add("缺少真实用户行为数据，不能验证分群有效性。");
        }
        if (nonEmptySegments <= 1 && totalUsers > 0) {
            warnings.add("当前真实数据只落入 " + nonEmptySegments + " 个分群，分群区分度不足。");
        }
        if (dominantRatio >= 85) {
            warnings.add("最大分群“" + dominantSegment + "”占比 " + dominantRatio + "%，不适合直接证明多类运营分群。");
        }
        suitability.put("suitableForOperationSegmentation", warnings.isEmpty());
        suitability.put("strictLifecycleSuitable", warnings.isEmpty());
        suitability.put("operationalSignalsUsable", nonEmptyOperationalSegments >= 2);
        suitability.put("totalUsers", totalUsers);
        suitability.put("nonEmptySegments", nonEmptySegments);
        suitability.put("nonEmptyOperationalSegments", nonEmptyOperationalSegments);
        suitability.put("dominantSegment", dominantSegment);
        suitability.put("dominantRatio", dominantRatio);
        suitability.put("warnings", warnings);
        return suitability;
    }

    private UserInterestProfile buildProfile(Long userId) {
        Map<String, Double> categoryScores = new LinkedHashMap<>();
        Map<String, Integer> behaviorCounts = new LinkedHashMap<>();

        mergeBehaviorScores(categoryScores, behaviorCounts, userId);
        mergeOrderScores(categoryScores, behaviorCounts, userId);
        mergeSearchScores(categoryScores, behaviorCounts, userId);

        double total = categoryScores.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Double> globalCategoryScores = buildGlobalCategoryScores();
        double globalTotal = globalCategoryScores.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map<String, Object>> categoryWeights = new ArrayList<>();
        if (total > 0) {
            for (Map.Entry<String, Double> entry : categoryScores.entrySet()) {
                double behaviorScore = entry.getValue();
                double ratio = behaviorScore * 100.0 / total;
                double globalRatio = globalTotal <= 0
                        ? 0
                        : globalCategoryScores.getOrDefault(entry.getKey(), 0D) * 100.0 / globalTotal;
                double lift = globalRatio <= 0 ? 1D : ratio / globalRatio;
                double rankingMultiplier = Math.max(0.65D, Math.min(1.85D, Math.sqrt(Math.max(lift, 0.01D))));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", entry.getKey());
                row.put("score", round(behaviorScore));
                row.put("behaviorScore", round(behaviorScore));
                row.put("ratio", round(ratio));
                row.put("globalRatio", round(globalRatio));
                row.put("lift", round(lift));
                row.put("rankingScore", round(behaviorScore * rankingMultiplier));
                categoryWeights.add(row);
            }
            categoryWeights.sort(Comparator.comparingDouble(item -> -number(item.get("rankingScore"))));
        }

        List<String> topCategories = new ArrayList<>();
        for (Map<String, Object> row : categoryWeights) {
            topCategories.add(String.valueOf(row.get("category")));
        }

        return new UserInterestProfile(
                total > 0,
                categoryWeights,
                topCategories,
                behaviorCounts,
                segmentName(behaviorCounts),
                "真实行为分 + 时间衰减 + 全站类目占比校准；score 为衰减后的行为分，rankingScore 用于排序。"
        );
    }

    private Map<String, Double> buildGlobalCategoryScores() {
        long now = System.currentTimeMillis();
        Map<String, Double> cached = globalCategoryScoreCache;
        if (!cached.isEmpty() && now - globalCategoryScoreCacheTime <= GLOBAL_CATEGORY_SCORE_CACHE_MILLIS) {
            return new LinkedHashMap<>(cached);
        }
        synchronized (this) {
            cached = globalCategoryScoreCache;
            now = System.currentTimeMillis();
            if (!cached.isEmpty() && now - globalCategoryScoreCacheTime <= GLOBAL_CATEGORY_SCORE_CACHE_MILLIS) {
                return new LinkedHashMap<>(cached);
            }
            Map<String, Double> refreshed = loadGlobalCategoryScores();
            globalCategoryScoreCache = refreshed;
            globalCategoryScoreCacheTime = now;
            return new LinkedHashMap<>(refreshed);
        }
    }

    private Map<String, Double> loadGlobalCategoryScores() {
        Map<String, Double> scores = new LinkedHashMap<>();
        mergeGlobalCategoryRows(scores, queryList(
                "SELECT COALESCE(c.name, '未分类') AS category, " +
                        "SUM((CASE WHEN ub.behavior_type IN ('buy', 'order', 'purchase') THEN 8 " +
                        "WHEN ub.behavior_type IN ('favorite', 'collect') THEN 3 " +
                        "WHEN ub.behavior_type IN ('cart', 'add_cart') THEN 2 " +
                        "WHEN ub.behavior_type = 'search' THEN 2 " +
                        "WHEN ub.behavior_type IN ('view', 'browse') THEN 1 ELSE 0 END) * " +
                        recencyWeightSql("ub.create_time") + ") AS score " +
                        "FROM user_behavior ub JOIN product p ON ub.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE ub.product_id IS NOT NULL GROUP BY COALESCE(c.name, '未分类')"
        ));
        mergeGlobalCategoryRows(scores, queryList(
                "SELECT COALESCE(c.name, '未分类') AS category, SUM(COALESCE(oi.quantity, 1) * 8 * " + recencyWeightSql("COALESCE(o.pay_time, o.create_time)") + ") AS score " +
                        "FROM `order` o JOIN order_item oi ON o.id = oi.order_id JOIN product p ON oi.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE o.status IN (1,2,3) GROUP BY COALESCE(c.name, '未分类')"
        ));
        for (Map<String, Object> row : queryList(
                "SELECT keyword, SUM(search_count) AS countValue, SUM(search_count * " + recencyWeightSql("update_time") + ") AS score FROM search_history WHERE keyword IS NOT NULL GROUP BY keyword"
        )) {
            String category = normalizeCategory(String.valueOf(row.get("keyword")));
            if (!category.isEmpty()) {
                scores.merge(category, Math.max(1D, number(row.get("score"))) * 2.0, Double::sum);
            }
        }
        return scores;
    }

    private void mergeGlobalCategoryRows(Map<String, Double> scores, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String category = normalizeCategory(String.valueOf(row.get("category")));
            double score = number(row.get("score"));
            if (!category.isEmpty() && score > 0) {
                scores.merge(category, score, Double::sum);
            }
        }
    }

    private void mergeBehaviorScores(Map<String, Double> scores, Map<String, Integer> counts, Long userId) {
        List<Map<String, Object>> rows = queryList(
                "SELECT COALESCE(c.name, '未分类') AS category, ub.behavior_type AS behaviorType, COUNT(*) AS countValue, " +
                        "SUM((CASE WHEN ub.behavior_type IN ('buy', 'order', 'purchase') THEN 8 " +
                        "WHEN ub.behavior_type IN ('favorite', 'collect') THEN 3 " +
                        "WHEN ub.behavior_type IN ('cart', 'add_cart') THEN 2 " +
                        "WHEN ub.behavior_type = 'search' THEN 2 " +
                        "WHEN ub.behavior_type IN ('view', 'browse') THEN 1 ELSE 0 END) * " +
                        recencyWeightSql("ub.create_time") + ") AS score " +
                        "FROM user_behavior ub JOIN product p ON ub.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        (userId == null ? "" : "WHERE ub.user_id = " + userId + " ") +
                        "GROUP BY COALESCE(c.name, '未分类'), ub.behavior_type"
        );

        for (Map<String, Object> row : rows) {
            String category = normalizeCategory(String.valueOf(row.get("category")));
            String behaviorType = String.valueOf(row.get("behaviorType"));
            int count = intNumber(row.get("countValue"));
            scores.merge(category, number(row.get("score")), Double::sum);
            counts.merge(behaviorType, count, Integer::sum);
        }
    }

    private void mergeOrderScores(Map<String, Double> scores, Map<String, Integer> counts, Long userId) {
        List<Map<String, Object>> rows = queryList(
                "SELECT COALESCE(c.name, '未分类') AS category, COALESCE(SUM(oi.quantity), 0) AS quantity, " +
                        "SUM(COALESCE(oi.quantity, 1) * 8 * " + recencyWeightSql("COALESCE(o.pay_time, o.create_time)") + ") AS score " +
                        "FROM order_item oi JOIN `order` o ON oi.order_id = o.id JOIN product p ON oi.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE o.status IN (1,2,3) " +
                        (userId == null ? "" : "AND o.user_id = " + userId + " ") +
                        "GROUP BY COALESCE(c.name, '未分类')"
        );

        for (Map<String, Object> row : rows) {
            String category = normalizeCategory(String.valueOf(row.get("category")));
            int quantity = intNumber(row.get("quantity"));
            scores.merge(category, number(row.get("score")), Double::sum);
            counts.merge("order", quantity, Integer::sum);
        }
    }

    private void mergeSearchScores(Map<String, Double> scores, Map<String, Integer> counts, Long userId) {
        List<Map<String, Object>> rows = queryList(
                "SELECT keyword, SUM(search_count) AS countValue, SUM(search_count * " + recencyWeightSql("update_time") + ") AS score FROM search_history " +
                        (userId == null ? "WHERE keyword IS NOT NULL " : "WHERE user_id = " + userId + " AND keyword IS NOT NULL ") +
                        "GROUP BY keyword ORDER BY countValue DESC LIMIT 200"
        );

        mergeKeywordRows(scores, counts, rows);

        List<Map<String, Object>> behaviorSearchRows = queryList(
                "SELECT search_keyword AS keyword, COUNT(*) AS countValue, SUM(" + recencyWeightSql("create_time") + ") AS score FROM user_behavior " +
                        (userId == null
                                ? "WHERE behavior_type = 'search' AND search_keyword IS NOT NULL "
                                : "WHERE user_id = " + userId + " AND behavior_type = 'search' AND search_keyword IS NOT NULL ") +
                        "GROUP BY search_keyword ORDER BY countValue DESC LIMIT 200"
        );
        mergeKeywordRows(scores, counts, behaviorSearchRows);
    }

    private void mergeKeywordRows(Map<String, Double> scores, Map<String, Integer> counts, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String category = normalizeCategory(String.valueOf(row.get("keyword")));
            if (category.isBlank()) continue;
            int count = intNumber(row.get("countValue"));
            double weightedCount = row.containsKey("score") ? Math.max(1D, number(row.get("score"))) : count;
            scores.merge(category, weightedCount * 2.0, Double::sum);
            counts.merge("search", count, Integer::sum);
        }
    }

    private String recencyWeightSql(String timeExpression) {
        return "(CASE " +
                "WHEN " + timeExpression + " >= DATE_SUB(NOW(), INTERVAL 30 DAY) THEN 1.40 " +
                "WHEN " + timeExpression + " >= DATE_SUB(NOW(), INTERVAL 90 DAY) THEN 1.00 " +
                "WHEN " + timeExpression + " >= DATE_SUB(NOW(), INTERVAL 365 DAY) THEN 0.60 " +
                "ELSE 0.35 END)";
    }

    private List<Map<String, Object>> loadCandidateProducts(List<String> topCategories) {
        String selectSql = "SELECT p.id, p.name, COALESCE(c.name, '未分类') AS category, p.price, p.stock, p.sales_count AS sales " +
                "FROM product p LEFT JOIN category c ON p.category_id = c.id " +
                "WHERE p.status = 1 AND p.stock > 0 ";
        List<Map<String, Object>> result = new ArrayList<>();
        appendUniqueProducts(result, queryList(selectSql + "ORDER BY COALESCE(p.sales_count, 0) DESC, p.id DESC LIMIT 160"));
        if (topCategories != null) {
            for (String category : topCategories.subList(0, Math.min(2, topCategories.size()))) {
                String normalized = normalizeCategory(category);
                if (normalized.isEmpty()) {
                    continue;
                }
                appendUniqueProducts(result, queryList(
                        selectSql + "AND COALESCE(c.name, '未分类') = '" + sqlLiteral(normalized) + "' " +
                                "ORDER BY COALESCE(p.sales_count, 0) DESC, p.id DESC LIMIT 80"
                ));
            }
        }
        return result;
    }

    private void appendUniqueProducts(List<Map<String, Object>> target, List<Map<String, Object>> candidates) {
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> item : target) {
            seen.add(String.valueOf(item.get("id")));
        }
        for (Map<String, Object> item : candidates) {
            if (seen.add(String.valueOf(item.get("id")))) {
                target.add(item);
            }
        }
    }

    private String sqlLiteral(String value) {
        return String.valueOf(value == null ? "" : value).replace("'", "''");
    }

    private List<Map<String, Object>> rerank(List<Map<String, Object>> products, UserInterestProfile profile, int limit) {
        Set<String> topTwo = new LinkedHashSet<>(profile.topCategories.subList(0, Math.min(2, profile.topCategories.size())));
        Map<String, Double> ratios = new HashMap<>();
        for (Map<String, Object> row : profile.categoryWeights) {
            ratios.put(normalizeCategory(String.valueOf(row.get("category"))), number(row.get("ratio")));
        }

        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> product : products) {
            Map<String, Object> item = new LinkedHashMap<>(product);
            String category = normalizeCategory(String.valueOf(item.getOrDefault("category", "")));
            double sales = number(item.get("sales"));
            double price = number(item.get("price"));
            double stock = number(item.get("stock"));
            double categoryRatio = ratios.getOrDefault(category, 0.0);
            boolean preferred = topTwo.contains(category);

            double score = categoryRatio * 12
                    + Math.log1p(Math.max(sales, 0)) * 8
                    + Math.min(stock, 200) * 0.02
                    - pricePenalty(price);
            if (preferred) {
                score += 800;
            } else {
                score -= 220;
            }

            item.put("category", category);
            item.put("interestScore", round(score));
            String reason = buildRecommendationReason(category, profile.topCategories, preferred);
            item.put("recommendReason", reason);
            item.put("explanation", reason);
            item.put("reason", reason);
            item.put("sourceChannel", preferred ? "兴趣匹配" : "多样性探索");
            scored.add(item);
        }

        scored.sort(Comparator.comparingDouble(item -> -number(item.get("interestScore"))));
        return enforceCoverage(scored, profile.topCategories, limit);
    }

    private String buildRecommendationReason(String category, List<String> topCategories, boolean preferred) {
        if (!preferred) {
            return "非主要偏好品类，仅作为少量探索推荐：" + category;
        }
        if (!topCategories.isEmpty() && category.equals(topCategories.get(0))) {
            return "命中用户 Top1 偏好品类：" + category;
        }
        return "命中用户 Top2 偏好品类：" + category;
    }

    private List<Map<String, Object>> enforceCoverage(List<Map<String, Object>> scored, List<String> topCategories, int limit) {
        Set<String> topTwo = new LinkedHashSet<>(topCategories.subList(0, Math.min(2, topCategories.size())));
        String topOne = topCategories.isEmpty() ? "" : topCategories.get(0);
        String secondPreference = topCategories.size() > 1 ? topCategories.get(1) : "";
        List<Map<String, Object>> preferred = new ArrayList<>();
        List<Map<String, Object>> explore = new ArrayList<>();
        for (Map<String, Object> item : scored) {
            String category = String.valueOf(item.getOrDefault("category", ""));
            if (topTwo.contains(category)) {
                preferred.add(item);
            } else {
                explore.add(item);
            }
        }

        int safeLimit = Math.min(limit, scored.size());
        int requiredPreferred = Math.min((int) Math.ceil(safeLimit * 0.65), preferred.size());
        List<Map<String, Object>> result = new ArrayList<>();
        int p = 0;
        int e = 0;

        for (Map<String, Object> product : preferred) {
            if (topOne.equals(String.valueOf(product.getOrDefault("category", "")))) {
                result.add(product);
                break;
            }
        }
        for (Map<String, Object> product : preferred) {
            if (secondPreference.equals(String.valueOf(product.getOrDefault("category", ""))) && !result.contains(product)) {
                result.add(product);
                break;
            }
        }
        while (result.size() < requiredPreferred && p < preferred.size()) {
            Map<String, Object> product = preferred.get(p++);
            if (!result.contains(product)) {
                result.add(product);
            }
        }
        while (result.size() < safeLimit && e < explore.size()) {
            Map<String, Object> product = explore.get(e++);
            if (!result.contains(product)) {
                result.add(product);
            }
        }
        while (result.size() < safeLimit && p < preferred.size()) {
            Map<String, Object> product = preferred.get(p++);
            if (!result.contains(product)) {
                result.add(product);
            }
        }
        return result;
    }

    private Map<String, Object> quality(List<Map<String, Object>> recommendations, UserInterestProfile profile) {
        List<String> topTwoCategories = new ArrayList<>(profile.topCategories.subList(0, Math.min(2, profile.topCategories.size())));
        Set<String> topTwo = new LinkedHashSet<>(topTwoCategories);
        int inspectSize = Math.min(10, recommendations.size());
        int topTwoHits = 0;
        int firstThreeHits = 0;

        for (int i = 0; i < inspectSize; i++) {
            String category = String.valueOf(recommendations.get(i).getOrDefault("category", ""));
            if (topTwo.contains(category)) topTwoHits++;
            if (i < 3 && topTwo.contains(category)) firstThreeHits++;
        }

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("topTwoCategories", topTwoCategories);
        quality.put("topTwoCoverage", inspectSize == 0 ? 0 : round(topTwoHits * 100.0 / inspectSize));
        quality.put("firstThreeCoverage", inspectSize == 0 ? 0 : round(firstThreeHits * 100.0 / Math.min(3, inspectSize)));
        quality.put("expectedRule", "Top10 中至少 60% 来自 Top2 偏好品类，前 3 个至少 2 个命中 Top2。");
        return quality;
    }

    private double behaviorWeight(String behaviorType) {
        if (behaviorType == null) return 1;
        if (behaviorType.equals("buy") || behaviorType.equals("order") || behaviorType.equals("purchase")) return 8;
        if (behaviorType.equals("favorite") || behaviorType.equals("collect")) return 3;
        if (behaviorType.equals("cart") || behaviorType.equals("add_cart")) return 2;
        if (behaviorType.equals("search")) return 2;
        return 1;
    }

    private double pricePenalty(double price) {
        if (price <= 0) return 0;
        return Math.log1p(price) * 0.8;
    }

    private String segmentName(Map<String, Integer> counts) {
        int order = counts.getOrDefault("order", 0);
        int cart = counts.getOrDefault("cart", 0) + counts.getOrDefault("add_cart", 0);
        int favorite = counts.getOrDefault("favorite", 0) + counts.getOrDefault("collect", 0);
        int search = counts.getOrDefault("search", 0);
        int view = counts.getOrDefault("view", 0) + counts.getOrDefault("browse", 0);

        if (order >= 2) return "复购用户";
        if (cart + favorite >= 2 && order == 0) return "高意向未购买";
        if (search >= 3 && order == 0) return "主动搜索用户";
        if (view > 0 && cart == 0 && order == 0) return "新客待激活";
        return "普通浏览用户";
    }

    private String normalizeCategory(String text) {
        if (text == null) return "";
        String normalizedText = text.trim();
        if (normalizedText.isEmpty() || "null".equalsIgnoreCase(normalizedText)) return "";
        for (String category : CATEGORY_KEYWORDS.keySet()) {
            if (normalizedText.equals(category)) return category;
        }
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (normalizedText.contains(entry.getKey())) return entry.getKey();
            for (String keyword : entry.getValue()) {
                if (normalizedText.contains(keyword)) return entry.getKey();
            }
        }
        return normalizedText;
    }

    private List<Map<String, Object>> queryList(String sql) {
        if (jdbcTemplate == null) return List.of();
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception ex) {
            log.warn("[InterestCommerce] SQL query failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private int intNumber(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private int medianInt(List<Map<String, Object>> rows, String field) {
        List<Integer> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            values.add(intNumber(row.get(field)));
        }
        values.sort(Integer::compareTo);
        return values.isEmpty() ? 0 : values.get(values.size() / 2);
    }

    private double medianDouble(List<Map<String, Object>> rows, String field) {
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            values.add(number(row.get(field)));
        }
        values.sort(Double::compareTo);
        return values.isEmpty() ? 0 : values.get(values.size() / 2);
    }

    private static class UserInterestProfile {
        private final boolean available;
        private final List<Map<String, Object>> categoryWeights;
        private final List<String> topCategories;
        private final Map<String, Integer> behaviorCounts;
        private final String segment;
        private final String rankingRule;

        private UserInterestProfile(
                boolean available,
                List<Map<String, Object>> categoryWeights,
                List<String> topCategories,
                Map<String, Integer> behaviorCounts,
                String segment,
                String rankingRule
        ) {
            this.available = available;
            this.categoryWeights = categoryWeights;
            this.topCategories = topCategories;
            this.behaviorCounts = behaviorCounts;
            this.segment = segment;
            this.rankingRule = rankingRule;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", available);
            result.put("categoryWeights", categoryWeights);
            result.put("topCategories", topCategories);
            result.put("behaviorCounts", behaviorCounts);
            result.put("segment", segment);
            result.put("rankingRule", rankingRule);
            return result;
        }
    }

    private static class Segment {
        private final String name;
        private final String rule;
        private final String operationAction;
        private final Map<String, Object> extra = new LinkedHashMap<>();
        private final List<Map<String, Object>> samples = new ArrayList<>();
        private int count = 0;

        private Segment(String name, String rule) {
            this(name, rule, "");
        }

        private Segment(String name, String rule, String operationAction) {
            this.name = name;
            this.rule = rule;
            this.operationAction = operationAction;
        }

        private void add(Map<String, Object> user) {
            count++;
            if (samples.size() < 20) {
                samples.add(user);
            }
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("rule", rule);
            map.put("operationAction", operationAction);
            map.put("count", count);
            if (!extra.isEmpty()) {
                map.putAll(extra);
            }
            map.put("samples", samples);
            return map;
        }
    }
}
