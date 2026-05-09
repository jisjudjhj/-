package com.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ecommerce.service.InterestCommerceService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/algorithm")
public class AdminAlgorithmQualityController {

    private static final Logger log = LoggerFactory.getLogger(AdminAlgorithmQualityController.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InterestCommerceService interestCommerceService;

    @GetMapping("/category-preference")
    public Map<String, Object> categoryPreference(@RequestParam(required = false) Long userId) {
        Map<String, Object> result = baseResult("用户品类偏好");
        List<Map<String, Object>> categories = loadCategoryPreference(userId);

        result.put("userId", userId);
        result.put("dataStatus", categories.isEmpty() ? "缺少用户行为或商品品类数据" : "可用");
        result.put("sourceFields", List.of(
                "user_behavior.user_id",
                "user_behavior.product_id",
                "user_behavior.search_keyword",
                "user_behavior.behavior_type",
                "`order`.user_id",
                "`order`.total_amount",
                "order_item.product_id",
                "order_item.quantity",
                "search_history.keyword",
                "search_history.search_count",
                "product.category_id",
                "category.name"
        ));
        result.put("categories", categories);
        result.put("rule", "购买 8 分，收藏 3 分，加购 2 分，搜索 2 分，浏览 1 分；按商品品类聚合后归一化。");
        result.put("interestCommerceLogic", "用搜索代表人找货，用浏览/加购/购买代表货找人后的反馈，二者共同决定品类偏好。");
        return result;
    }

    @GetMapping("/recommendation-check")
    public Map<String, Object> recommendationCheck(@RequestParam Long userId) {
        return interestCommerceService.auditRecommendation(userId);
    }

    @GetMapping("/recommendations")
    public Map<String, Object> recommendations(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return interestCommerceService.buildRecommendation(userId, limit);
    }

    public Map<String, Object> legacyRecommendationCheck(@RequestParam Long userId) {
        Map<String, Object> result = baseResult("推荐一致性检查");
        List<Map<String, Object>> categories = loadCategoryPreference(userId);
        List<Map<String, Object>> products = loadCandidateProducts();

        if (categories.isEmpty() || products.isEmpty()) {
            result.put("dataStatus", "缺少用户偏好或候选商品数据");
            result.put("problems", List.of("无法判断推荐是否符合画像，因为用户行为或商品数据不足。"));
            result.put("topCategories", categories);
            result.put("topProducts", products);
            return result;
        }

        List<String> topTwo = categories.stream()
                .limit(2)
                .map(item -> String.valueOf(item.get("category")))
                .collect(Collectors.toList());

        List<Map<String, Object>> reranked = rerankProducts(products, topTwo);
        int inspectSize = Math.min(10, reranked.size());
        int matched = 0;
        for (int i = 0; i < inspectSize; i++) {
            String category = String.valueOf(reranked.get(i).getOrDefault("category", ""));
            if (topTwo.contains(category)) {
                matched++;
            }
        }

        List<String> problems = new ArrayList<>();
        if (inspectSize > 0 && matched < Math.min(6, inspectSize)) {
            problems.add("Top10 中偏好品类命中不足，说明热门或协同过滤权重可能过高。");
        }
        if (inspectSize >= 3) {
            int firstThreeMatched = 0;
            for (int i = 0; i < 3; i++) {
                String category = String.valueOf(reranked.get(i).getOrDefault("category", ""));
                if (topTwo.contains(category)) firstThreeMatched++;
            }
            if (firstThreeMatched < 2) {
                problems.add("前 3 个商品没有优先命中 Top2 偏好品类。");
            }
        }

        result.put("dataStatus", problems.isEmpty() ? "可用" : "需要调整");
        result.put("topCategories", categories);
        result.put("topProducts", reranked.subList(0, inspectSize));
        result.put("topTwoCoverage", inspectSize == 0 ? 0 : round(matched * 100.0 / inspectSize));
        result.put("problems", problems);
        result.put("rule", "Top10 至少 6 个来自用户 Top2 偏好品类；前 3 个至少 2 个来自 Top2 偏好品类。");
        return result;
    }

    @GetMapping("/user-segments")
    public Map<String, Object> userSegments() {
        Map<String, Object> result = baseResult("用户分群");
        List<Map<String, Object>> users = loadUserSignals();
        if (users.isEmpty()) {
            result.put("dataStatus", "缺少用户、订单或行为数据");
            result.put("segments", List.of());
            result.put("rule", "没有真实数据时不生成虚构分群。");
            return result;
        }

        Map<String, Map<String, Object>> segments = new LinkedHashMap<>();
        segments.put("高意向未购买", segment("高意向未购买", "加购或收藏较多，但近 30 天没有支付"));
        segments.put("复购用户", segment("复购用户", "累计支付订单数大于等于 2"));
        segments.put("价格敏感", segment("价格敏感", "加购较多但支付少，适合优惠券促转化"));
        segments.put("新客待激活", segment("新客待激活", "有浏览行为但暂无加购和支付"));
        segments.put("主动搜索用户", segment("主动搜索用户", "搜索较多但未成交，适合补充对应品类商品"));

        for (Map<String, Object> user : users) {
            int orderCount = intValue(user.get("orderCount"));
            int cartCount = intValue(user.get("cartCount"));
            int favoriteCount = intValue(user.get("favoriteCount"));
            int viewCount = intValue(user.get("viewCount"));
            int searchCount = intValue(user.get("searchCount"));

            if (orderCount >= 2) addUser(segments.get("复购用户"), user);
            if ((cartCount + favoriteCount) >= 2 && orderCount == 0) addUser(segments.get("高意向未购买"), user);
            if (cartCount >= 2 && orderCount <= 1) addUser(segments.get("价格敏感"), user);
            if (viewCount > 0 && cartCount == 0 && orderCount == 0) addUser(segments.get("新客待激活"), user);
            if (searchCount >= 3 && orderCount == 0) addUser(segments.get("主动搜索用户"), user);
        }

        result.put("dataStatus", "可用");
        result.put("segments", new ArrayList<>(segments.values()));
        result.put("sourceFields", List.of("user.id", "`order`.user_id", "`order`.total_amount", "search_history.keyword", "search_history.search_count", "user_behavior.user_id", "user_behavior.behavior_type"));
        result.put("rule", "用真实浏览、收藏、加购、支付信号打标签，分群可以为空，但不编造用户。");
        return result;
    }

    @GetMapping("/business-analysis")
    public Map<String, Object> businessAnalysis() {
        Map<String, Object> result = baseResult("运营数据分析");
        List<Map<String, Object>> categorySales = queryList(
                "SELECT COALESCE(c.name, '未分类') AS category, COUNT(oi.id) AS orderItems, COALESCE(SUM(oi.price * oi.quantity), 0) AS amount " +
                        "FROM order_item oi JOIN `order` o ON oi.order_id = o.id JOIN product p ON oi.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE o.status IN (1,2,3) GROUP BY COALESCE(c.name, '未分类') ORDER BY amount DESC LIMIT 10"
        );
        List<Map<String, Object>> lowStock = queryList(
                "SELECT p.id, p.name, COALESCE(c.name, '未分类') AS category, p.stock FROM product p LEFT JOIN category c ON p.category_id = c.id WHERE p.status = 1 AND p.stock <= 20 ORDER BY p.stock ASC LIMIT 10"
        );

        result.put("dataStatus", categorySales.isEmpty() && lowStock.isEmpty() ? "缺少订单明细或商品库存数据" : "可用");
        result.put("categorySales", categorySales);
        result.put("lowStockProducts", lowStock);
        result.put("rule", "运营分析只展示真实订单明细和库存，不使用模拟销售额。");
        return result;
    }

    private List<Map<String, Object>> loadCategoryPreference(Long userId) {
        Map<String, Double> scores = new LinkedHashMap<>();

        mergeCategoryScores(scores, queryList(
                "SELECT COALESCE(c.name, '未分类') AS category, " +
                        "SUM(CASE " +
                        "WHEN ub.behavior_type IN ('buy', 'order', 'purchase') THEN 8 " +
                        "WHEN ub.behavior_type IN ('favorite', 'collect') THEN 3 " +
                        "WHEN ub.behavior_type IN ('cart', 'add_cart') THEN 2 " +
                        "WHEN ub.behavior_type = 'search' THEN 2 " +
                        "ELSE 1 END) AS score " +
                        "FROM user_behavior ub JOIN product p ON ub.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        (userId == null ? "" : "WHERE ub.user_id = " + userId + " ") +
                        "GROUP BY COALESCE(c.name, '未分类') ORDER BY score DESC"
        ));

        mergeCategoryScores(scores, queryList(
                "SELECT COALESCE(c.name, '未分类') AS category, " +
                        "SUM(COALESCE(oi.quantity, 1) * 8) AS score " +
                        "FROM order_item oi JOIN `order` o ON oi.order_id = o.id JOIN product p ON oi.product_id = p.id LEFT JOIN category c ON p.category_id = c.id " +
                        "WHERE o.status IN (1,2,3) " +
                        (userId == null ? "" : "AND o.user_id = " + userId + " ") +
                        "GROUP BY COALESCE(c.name, '未分类') ORDER BY score DESC"
        ));

        mergeSearchKeywordScores(scores, queryList(
                "SELECT keyword, SUM(search_count) AS countValue FROM search_history " +
                        (userId == null ? "WHERE keyword IS NOT NULL " : "WHERE user_id = " + userId + " AND keyword IS NOT NULL ") +
                        "GROUP BY keyword ORDER BY countValue DESC LIMIT 200"
        ));

        mergeSearchKeywordScores(scores, queryList(
                "SELECT search_keyword AS keyword, COUNT(*) AS countValue FROM user_behavior " +
                        (userId == null
                                ? "WHERE behavior_type = 'search' AND search_keyword IS NOT NULL "
                                : "WHERE user_id = " + userId + " AND behavior_type = 'search' AND search_keyword IS NOT NULL ") +
                        "GROUP BY search_keyword ORDER BY countValue DESC LIMIT 200"
        ));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", entry.getKey());
            row.put("score", round(entry.getValue()));
            rows.add(row);
        }
        rows.sort(Comparator.comparingDouble(item -> -doubleValue(item.get("score"))));

        double total = rows.stream().mapToDouble(item -> doubleValue(item.get("score"))).sum();
        if (total <= 0) return rows;
        for (Map<String, Object> row : rows) {
            double score = doubleValue(row.get("score"));
            row.put("ratio", round(score * 100.0 / total));
        }
        return rows;
    }

    private void mergeCategoryScores(Map<String, Double> scores, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String category = normalizeCategory(String.valueOf(row.getOrDefault("category", "")));
            if (category.isBlank()) continue;
            scores.merge(category, doubleValue(row.get("score")), Double::sum);
        }
    }

    private void mergeSearchKeywordScores(Map<String, Double> scores, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String keyword = String.valueOf(row.getOrDefault("keyword", ""));
            String category = normalizeCategory(keyword);
            if (category.isBlank()) continue;
            scores.merge(category, Math.max(1D, doubleValue(row.get("countValue"))) * 2.0, Double::sum);
        }
    }

    private String normalizeCategory(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            return "";
        }
        if (normalized.contains("电脑办公") || normalized.contains("轻办公设备") || normalized.contains("商务办公") || normalized.contains("显示设备") || normalized.contains("办公电脑") || normalized.contains("办公键鼠") || normalized.contains("显示器") || normalized.contains("键鼠") || normalized.contains("鼠标") || normalized.contains("键盘") || normalized.contains("打印机") || normalized.contains("扩展坞") || normalized.contains("双屏") || normalized.contains("高刷") || normalized.contains("轻办公")) {
            return "电脑办公";
        }
        if (normalized.contains("图书") || normalized.contains("文具") || normalized.contains("文创") || normalized.contains("笔记") || normalized.contains("阅读") || normalized.contains("文学") || normalized.contains("小说") || normalized.contains("钢笔") || normalized.contains("中性笔")) {
            return "图书文具";
        }
        if (normalized.contains("食品") || normalized.contains("生鲜") || normalized.contains("饮品") || normalized.contains("水果") || normalized.contains("零食") || normalized.contains("咖啡") || normalized.contains("茶") || normalized.contains("牛奶") || normalized.contains("矿泉水") || normalized.contains("肉脯") || normalized.contains("燕麦")) {
            return "食品生鲜";
        }
        if (normalized.contains("手机") || normalized.contains("数码") || normalized.contains("智能手机") || normalized.contains("耳机") || normalized.contains("相机") || normalized.contains("智能手表") || normalized.contains("备用机")) {
            return "手机数码";
        }
        if (normalized.contains("家电") || normalized.contains("电器") || normalized.contains("厨房") || normalized.contains("厨电") || normalized.contains("净化器") || normalized.contains("洗衣机") || normalized.contains("冰箱") || normalized.contains("空调") || normalized.contains("微波炉") || normalized.contains("电饭煲") || normalized.contains("台灯")) {
            return "家用电器";
        }
        if (normalized.contains("美妆") || normalized.contains("护肤") || normalized.contains("彩妆") || normalized.contains("香水") || normalized.contains("口红") || normalized.contains("精华") || normalized.contains("口腔") || normalized.contains("牙刷") || normalized.contains("牙膏") || normalized.contains("个护") || normalized.contains("护理")) {
            return "美妆护肤";
        }
        if (normalized.contains("服饰") || normalized.contains("鞋") || normalized.contains("包") || normalized.contains("穿搭") || normalized.contains("羽绒服") || normalized.contains("差旅收纳")) {
            return "服饰鞋包";
        }
        if (normalized.contains("运动") || normalized.contains("户外") || normalized.contains("露营") || normalized.contains("健身") || normalized.contains("骑行") || normalized.contains("跑步") || normalized.contains("羽毛球")) {
            return "运动户外";
        }
        if (normalized.contains("母婴") || normalized.contains("奶瓶") || normalized.contains("纸尿裤") || normalized.contains("玩具") || normalized.contains("儿童") || normalized.contains("积木") || normalized.contains("安抚")) {
            return "母婴玩具";
        }
        if (normalized.contains("家居") || normalized.contains("家装") || normalized.contains("收纳") || normalized.contains("窗帘") || normalized.contains("书架") || normalized.contains("门锁") || normalized.contains("地毯") || normalized.contains("安防") || normalized.contains("清洁")) {
            return "家居家装";
        }
        return normalized;
    }

    private List<Map<String, Object>> loadCandidateProducts() {
        return queryList(
                "SELECT p.id, p.name, COALESCE(c.name, '未分类') AS category, p.price, p.stock, p.sales_count AS sales " +
                        "FROM product p LEFT JOIN category c ON p.category_id = c.id WHERE p.status = 1 AND p.stock > 0 ORDER BY COALESCE(p.sales_count, 0) DESC, p.id DESC LIMIT 100"
        );
    }

    private List<Map<String, Object>> rerankProducts(List<Map<String, Object>> products, List<String> topTwo) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> product : products) {
            Map<String, Object> item = new LinkedHashMap<>(product);
            String category = normalizeCategory(String.valueOf(item.getOrDefault("category", "")));
            double sales = doubleValue(item.get("sales"));
            double categoryScore = topTwo.contains(category) ? 1000 : -200;
            item.put("category", category);
            item.put("recommendScore", round(categoryScore + sales));
            item.put("reason", topTwo.contains(category) ? "命中用户 Top2 偏好品类" : "非主要偏好品类，仅可少量探索");
            copy.add(item);
        }
        copy.sort(Comparator.comparingDouble(item -> -doubleValue(item.get("recommendScore"))));
        return copy;
    }

    private List<Map<String, Object>> loadUserSignals() {
        return queryList(
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
                        "GROUP BY u.id LIMIT 200"
        );
    }

    private Map<String, Object> baseResult(String title) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("generatedFrom", "database");
        return result;
    }

    private Map<String, Object> segment(String name, String rule) {
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("name", name);
        segment.put("rule", rule);
        segment.put("count", 0);
        segment.put("users", new ArrayList<Map<String, Object>>());
        return segment;
    }

    @SuppressWarnings("unchecked")
    private void addUser(Map<String, Object> segment, Map<String, Object> user) {
        List<Map<String, Object>> users = (List<Map<String, Object>>) segment.get("users");
        if (users.size() < 20) {
            users.add(user);
        }
        segment.put("count", intValue(segment.get("count")) + 1);
    }

    private List<Map<String, Object>> queryList(String sql) {
        if (jdbcTemplate == null) {
            return new ArrayList<>();
        }
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception ex) {
            log.warn("[AdminAlgorithmQuality] SQL query failed: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double doubleValue(Object value) {
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
}
