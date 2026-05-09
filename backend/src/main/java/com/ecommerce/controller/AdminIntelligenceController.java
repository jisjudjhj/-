package com.ecommerce.controller;

import com.ecommerce.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/intelligence")
public class AdminIntelligenceController {

    private static final Logger log = LoggerFactory.getLogger(AdminIntelligenceController.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Number> behavior = collectBehaviorSignals();
        Map<String, Object> summary = buildSummary(behavior);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("title", "推荐算法分析");
        overview.put("positioning", "说明混合推荐权重、用户行为依据和处理入口");
        overview.put("summary", summary);
        overview.put("hybridRecommendation", buildHybridRecommendation(behavior));
        overview.put("behaviorAnalysis", buildBehaviorAnalysis(behavior));
        overview.put("innovationPoints", buildInnovationPoints());
        overview.put("comparisonResults", buildComparisonResults(behavior));
        overview.put("priorityActions", buildPriorityActions(behavior));
        overview.put("generatedAt", LocalDateTime.now().toString());
        return Result.success(overview);
    }

    private Map<String, Object> buildSummary(Map<String, Number> behavior) {
        int activeProducts = queryInt("SELECT COUNT(*) FROM product WHERE status = 1", 0);
        int lowStockProducts = queryInt("SELECT COUNT(*) FROM product WHERE status = 1 AND stock <= 20", 0);
        int coupons = queryInt("SELECT COUNT(*) FROM coupon WHERE status = 1", 0);
        int seckill = queryInt("SELECT COUNT(*) FROM seckill_activity WHERE publish_status IN (0, 1)", 0);
        double todayGmv = queryDouble("SELECT COALESCE(SUM(total_amount), 0) FROM `order` WHERE status IN (1,2,3) AND DATE(COALESCE(pay_time, create_time)) = CURDATE()", 0);

        int behaviorSignals = totalBehavior(behavior);
        int availableSources = 0;
        if (activeProducts > 0) availableSources++;
        if (behavior.get("view").intValue() + behavior.get("favorite").intValue() + behavior.get("cart").intValue() > 0) availableSources++;
        if (behavior.get("search").intValue() > 0) availableSources++;
        if (behavior.get("order").intValue() > 0) availableSources++;
        if (coupons + seckill > 0) availableSources++;
        int healthScore = availableSources == 0
                ? 0
                : clamp(availableSources * 16 + Math.min(20, behaviorSignals / 80) - Math.min(12, lowStockProducts / 3), 0, 100);
        String dataStatus = availableSources == 0
                ? "缺少真实数据"
                : (behaviorSignals == 0 || activeProducts == 0 ? "数据不足" : "可用");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("activeProductCount", activeProducts);
        summary.put("lowStockProducts", lowStockProducts);
        summary.put("couponCount", coupons);
        summary.put("seckillActivityCount", seckill);
        summary.put("todayGmv", round(todayGmv));
        summary.put("behaviorSignalCount", behaviorSignals);
        summary.put("healthScore", healthScore);
        summary.put("dataStatus", dataStatus);
        summary.put("scoreBasis", "健康分由真实商品、行为、搜索、订单、营销数据源完整度和低库存扣分计算，不使用固定高分兜底。");
        return summary;
    }

    private Map<String, Object> buildHybridRecommendation(Map<String, Number> behavior) {
        int view = behavior.get("view").intValue();
        int cart = behavior.get("cart").intValue();
        int order = behavior.get("order").intValue();
        int search = behavior.get("search").intValue();
        int total = Math.max(totalBehavior(behavior), 1);

        double cartRate = rate(cart, view);
        double orderRate = rate(order, Math.max(cart, 1));
        double searchShare = search * 1.0 / total;

        double behaviorWeight = 0.45;
        double contentWeight = 0.25;
        double cfWeight = 0.15;
        double trendWeight = 0.10;
        double explorationWeight = 0.05;

        if (total < 80) {
            behaviorWeight = 0.32;
            contentWeight = 0.30;
            cfWeight = 0.10;
            trendWeight = 0.20;
            explorationWeight = 0.08;
        } else if (cartRate >= 0.18 || orderRate >= 0.35) {
            behaviorWeight = 0.52;
            contentWeight = 0.22;
            cfWeight = 0.12;
            trendWeight = 0.09;
            explorationWeight = 0.05;
        } else if (searchShare >= 0.22) {
            behaviorWeight = 0.48;
            contentWeight = 0.27;
            cfWeight = 0.12;
            trendWeight = 0.08;
            explorationWeight = 0.05;
        }

        List<Map<String, Object>> weights = new ArrayList<>();
        weights.add(weight("品类偏好行为", behaviorWeight, "浏览、搜索、加购、收藏、下单对应的品类占比", "用户偏好品类必须进入最终排序，不能只用于展示。"));
        weights.add(weight("商品内容相似", contentWeight, "类目、标题关键词、价格带", "保证推荐商品和用户偏好内容相近。"));
        weights.add(weight("协同过滤", cfWeight, "相似用户交互商品", "补充相似用户喜欢的商品，但不能超过用户本人偏好。"));
        weights.add(weight("热门趋势", trendWeight, "销量、搜索热度、近期浏览", "只作为补充，避免热门商品冲掉个性化结果。"));
        weights.add(weight("多样性探索", explorationWeight, "少量非偏好品类", "探索权重控制在低位，避免随机扰动破坏排序稳定。"));

        Map<String, Object> algorithm = new LinkedHashMap<>();
        algorithm.put("formula", formula(weights));
        algorithm.put("weights", weights);
        algorithm.put("adjustReason", behaviorAdjustReason(total, cartRate, orderRate, searchShare));
        algorithm.put("implementation", "建议先把真实行为转成品类偏好分，再融合内容相似、协同过滤、热门趋势，并用 Top2 偏好品类做重排约束。");
        algorithm.put("result", "这里展示的是当前数据下的建议权重和约束，不声明没有实验支撑的转化改善幅度。");
        return algorithm;
    }

    private List<Map<String, Object>> buildBehaviorAnalysis(Map<String, Number> behavior) {
        int view = behavior.get("view").intValue();
        int search = behavior.get("search").intValue();
        int favorite = behavior.get("favorite").intValue();
        int cart = behavior.get("cart").intValue();
        int order = behavior.get("order").intValue();

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(analysis("浏览到加购", view, cart, "判断商品详情页和推荐入口是否有效", "/admin/products"));
        rows.add(analysis("加购到下单", Math.max(cart, 1), order, "判断价格、优惠券和库存是否影响转化", "/admin/coupons"));
        rows.add(analysis("搜索到成交", Math.max(search, 1), order, "判断搜索词和商品供给是否匹配", "/admin/products"));
        rows.add(analysis("收藏沉淀", Math.max(view, 1), favorite, "判断用户是否形成复访意愿", "/admin/users"));
        return rows;
    }

    private List<Map<String, Object>> buildInnovationPoints() {
        List<Map<String, Object>> points = new ArrayList<>();
        points.add(point(
                "混合推荐不是只按销量",
                "普通商城常用销量榜，容易让热门商品越来越热，新品和长尾商品没有机会。",
                "以用户 Top2 偏好品类为强约束，热门趋势和探索推荐只做补充，并把库存作为过滤条件。",
                "推荐列表可以被一致性规则检查，避免热门商品冲掉个性化。",
                "/admin/products",
                "调整商品池"
        ));
        points.add(point(
                "用户行为能解释运营动作",
                "只看订单太晚，浏览、搜索、加购这些前置信号能提前发现问题。",
                "把浏览、搜索、收藏、加购、下单做成转化漏斗，用转化率反推商品、优惠券或库存问题。",
                "管理端可以直接知道应该补货、发券还是优化商品标题。",
                "/admin/users",
                "查看用户分群"
        ));
        points.add(point(
                "建议可以一键处理",
                "答辩时不只是展示数据，而是展示系统能把数据变成可执行动作。",
                "每条建议都带依据字段和 actionRoute，点击后跳到商品、优惠券、秒杀或用户页面处理。",
                "形成“发现问题、说明原因、跳转处理”的闭环。",
                "/admin/seckill",
                "配置活动"
        ));
        return points;
    }

    private List<Map<String, Object>> buildComparisonResults(Map<String, Number> behavior) {
        int view = behavior.get("view").intValue();
        int cart = behavior.get("cart").intValue();
        int order = behavior.get("order").intValue();
        double cartRate = rate(cart, view);
        double payRate = rate(order, Math.max(cart, 1));

        List<Map<String, Object>> results = new ArrayList<>();
        results.add(compare("浏览到加购", cartRate * 100, 18, "当前值来自真实浏览和加购行为，目标值用于判断推荐入口是否有效。"));
        results.add(compare("加购到下单", payRate * 100, 35, "当前值来自真实加购和订单行为，目标值用于判断价格、优惠券和库存是否影响成交。"));
        results.add(compare("行为数据完整度", totalBehavior(behavior) > 0 ? 100 : 0, 100, "只有存在真实用户行为时，推荐算法和分群结果才判定为可用。"));
        return results;
    }

    private List<Map<String, Object>> buildPriorityActions(Map<String, Number> behavior) {
        int lowStock = queryInt("SELECT COUNT(*) FROM product WHERE status = 1 AND stock <= 20", 0);
        int search = behavior.get("search").intValue();
        int cart = behavior.get("cart").intValue();
        int order = behavior.get("order").intValue();

        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(action(
                "搜索需求缺口",
                search > 0 ? "因为存在真实搜索行为 " + search + " 次，需要检查搜索词和商品供给是否匹配" : "当前缺少搜索记录，不能判断搜索需求缺口",
                search > 0 ? "补充商品关键词或上架相近商品" : "先接入或检查搜索记录采集",
                "/admin/products",
                "去商品管理"
        ));
        actions.add(action(
                "推荐权重调优",
                "因为用户行为信号 " + totalBehavior(behavior) + " 条，加购 " + cart + " 次，下单 " + order + " 次",
                "保持用户行为为主权重，同时保留内容相似和库存约束",
                "/admin/products",
                "调整商品池"
        ));
        actions.add(action(
                "优惠券促转化",
                "因为加购到下单存在流失，适合对高意向用户发券",
                "对加购未支付用户配置定向优惠券",
                "/admin/coupons",
                "去优惠券管理"
        ));
        actions.add(action(
                "秒杀活动筛选",
                "因为低库存商品 " + lowStock + " 个，不适合直接进入高并发秒杀",
                "只选择库存充足、浏览高、转化稳定的商品参加秒杀",
                "/admin/seckill",
                "去秒杀活动"
        ));
        actions.add(action(
                "用户分群运营",
                "因为搜索行为 " + search + " 次，可以区分强需求、观望和价格敏感用户",
                "把用户分成高意向、价格敏感、复访沉淀三类分别运营",
                "/admin/users",
                "去用户分群"
        ));
        return actions;
    }

    private Map<String, Number> collectBehaviorSignals() {
        Map<String, Number> behavior = new LinkedHashMap<>();
        behavior.put("view", queryInt("SELECT COUNT(*) FROM user_behavior WHERE behavior_type IN ('view', 'browse')", 0));
        behavior.put("search", queryInt("SELECT COALESCE(SUM(search_count), 0) FROM search_history", 0)
                + queryInt("SELECT COUNT(*) FROM user_behavior WHERE behavior_type = 'search' AND search_keyword IS NOT NULL", 0));
        behavior.put("favorite", queryInt("SELECT COUNT(*) FROM user_behavior WHERE behavior_type IN ('favorite', 'collect')", 0));
        behavior.put("cart", queryInt("SELECT COUNT(*) FROM user_behavior WHERE behavior_type IN ('cart', 'add_cart')", 0));
        behavior.put("order", queryInt("SELECT COUNT(*) FROM `order` WHERE status IN (1, 2, 3)", 0));
        return behavior;
    }

    private Map<String, Object> weight(String label, double value, String basis, String why) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("weight", round(value * 100));
        item.put("basis", basis);
        item.put("why", why);
        return item;
    }

    private Map<String, Object> analysis(String label, int from, int to, String insight, String actionRoute) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("from", from);
        item.put("to", to);
        item.put("rate", round(rate(to, from) * 100));
        item.put("insight", insight);
        item.put("evidence", "因为前序行为 " + from + " 次，后续行为 " + to + " 次，转化率 " + round(rate(to, from) * 100) + "%");
        item.put("actionRoute", actionRoute);
        item.put("actionLabel", "去处理");
        return item;
    }

    private Map<String, Object> point(String title, String whyUse, String how, String result, String route, String actionLabel) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("whyUse", whyUse);
        item.put("how", how);
        item.put("result", result);
        item.put("actionRoute", route);
        item.put("actionLabel", actionLabel);
        return item;
    }

    private Map<String, Object> compare(String metric, double current, double target, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("metric", metric);
        item.put("current", round(current));
        item.put("referenceTarget", round(target));
        item.put("gapToTarget", round(target - current));
        item.put("reason", reason);
        return item;
    }

    private Map<String, Object> action(String title, String evidence, String suggestion, String route, String actionLabel) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("evidence", evidence);
        item.put("suggestion", suggestion);
        item.put("actionRoute", route);
        item.put("actionLabel", actionLabel);
        return item;
    }

    private String formula(List<Map<String, Object>> weights) {
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> weight : weights) {
            parts.add(weight.get("label") + " " + weight.get("weight") + "%");
        }
        return "推荐分 = " + String.join(" + ", parts);
    }

    private String behaviorAdjustReason(int total, double cartRate, double orderRate, double searchShare) {
        if (total < 80) {
            return "当前行为数据较少，降低个人行为权重，增加商品内容和热度，避免冷启动推荐失真。";
        }
        if (cartRate >= 0.18 || orderRate >= 0.35) {
            return "当前用户行为已形成明显购买意图，提高个人行为权重，让推荐更个性化。";
        }
        if (searchShare >= 0.22) {
            return "搜索占比较高，说明用户需求明确，适当提高商品内容匹配权重。";
        }
        return "当前行为量稳定，采用默认混合权重，兼顾个性化、热度和库存。";
    }

    private int totalBehavior(Map<String, Number> behavior) {
        int total = 0;
        for (Number value : behavior.values()) {
            total += value.intValue();
        }
        return total;
    }

    private int queryInt(String sql, int fallback) {
        Number value = queryNumber(sql, fallback);
        return value == null ? fallback : value.intValue();
    }

    private double queryDouble(String sql, double fallback) {
        Number value = queryNumber(sql, fallback);
        return value == null ? fallback : value.doubleValue();
    }

    private Number queryNumber(String sql, Number fallback) {
        if (jdbcTemplate == null) {
            return fallback;
        }
        try {
            Number value = jdbcTemplate.queryForObject(sql, Number.class);
            return value == null ? fallback : value;
        } catch (Exception ex) {
            log.warn("[AdminIntelligence] SQL query failed: {}", ex.getMessage());
            return fallback;
        }
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return numerator * 1.0 / denominator;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
