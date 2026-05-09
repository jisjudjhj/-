package com.ecommerce.config;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ControllerAdvice
public class RecommendationResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final List<String> PRODUCT_LIST_KEYS = List.of(
            "recommendations", "recommendProducts", "recommendedProducts", "products",
            "items", "list", "records", "hybrid", "hybridList"
    );

    private static final List<String> CATEGORY_WEIGHT_KEYS = List.of(
            "categoryWeights", "categoryWeight", "categoryPreference", "categoryPreferences",
            "behaviorCategoryWeights", "behaviorCategories", "preferenceCategories",
            "categoryDistribution", "preferCategories"
    );

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("电脑办公", List.of("电脑办公", "轻办公设备", "商务办公", "显示设备", "办公电脑", "办公键鼠", "显示器", "键鼠", "鼠标", "键盘", "打印机", "扩展坞", "双屏", "高刷", "轻办公"));
        CATEGORY_KEYWORDS.put("图书文具", List.of("图书文具", "学习文具", "书写文创", "文创图书", "办公文具", "图书", "文具", "文创", "笔记", "阅读", "文学", "小说", "钢笔", "中性笔"));
        CATEGORY_KEYWORDS.put("食品生鲜", List.of("食品生鲜", "生鲜礼盒", "饮品零食", "食品", "生鲜", "水果", "蔬菜", "饮品", "茶", "咖啡", "零食", "牛奶", "矿泉水", "肉脯", "燕麦"));
        CATEGORY_KEYWORDS.put("手机数码", List.of("手机数码", "智能手机", "手机", "数码", "耳机", "相机", "智能手表", "备用机"));
        CATEGORY_KEYWORDS.put("家用电器", List.of("家用电器", "家电", "电器", "厨房电器", "厨电", "净化器", "洗衣机", "冰箱", "空调", "微波炉", "电饭煲", "台灯"));
        CATEGORY_KEYWORDS.put("美妆护肤", List.of("美妆护肤", "美妆", "护肤", "彩妆", "香水", "口红", "精华", "口腔护理", "口腔", "牙刷", "牙膏", "个护", "护理"));
        CATEGORY_KEYWORDS.put("服饰鞋包", List.of("服饰鞋包", "服饰", "鞋", "包", "穿搭", "衣", "羽绒服", "差旅收纳"));
        CATEGORY_KEYWORDS.put("运动户外", List.of("运动户外", "露营出游", "城市骑行", "运动", "户外", "露营", "健身", "骑行", "跑步", "羽毛球"));
        CATEGORY_KEYWORDS.put("母婴玩具", List.of("母婴玩具", "陪伴玩偶", "母婴", "奶瓶", "纸尿裤", "玩具", "儿童", "积木", "安抚"));
        CATEGORY_KEYWORDS.put("家居家装", List.of("家居家装", "智能安防", "家务清洁", "家居", "家装", "收纳", "窗帘", "书架", "门锁", "地毯"));
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        String path = request.getURI().getPath().toLowerCase(Locale.ROOT);
        if (!path.contains("recommend") || body == null || body instanceof String) {
            return body;
        }

        try {
            PreferenceContext context = PreferenceContext.from(body);
            if (context.topCategories.isEmpty()) {
                return body;
            }
            rerankNested(body, context, new HashSet<>());
        } catch (Exception ignored) {
            return body;
        }

        return body;
    }

    @SuppressWarnings("unchecked")
    private void rerankNested(Object node, PreferenceContext context, Set<Integer> visited) {
        if (node == null) return;
        int identity = System.identityHashCode(node);
        if (visited.contains(identity)) return;
        visited.add(identity);

        if (node instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) node;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof List<?> && isProductList(String.valueOf(entry.getKey()), (List<?>) value)) {
                    List<?> list = (List<?>) value;
                    rerankProductList((List<Map<String, Object>>) (List<?>) list, context);
                } else {
                    rerankNested(value, context, visited);
                }
            }
            return;
        }

        if (node instanceof List<?>) {
            List<?> list = (List<?>) node;
            if (isProductList("", list)) {
                rerankProductList((List<Map<String, Object>>) (List<?>) list, context);
                return;
            }
            for (Object item : list) {
                rerankNested(item, context, visited);
            }
        }
    }

    private boolean isProductList(String key, List<?> list) {
        if (list.isEmpty()) return false;
        boolean keyLooksLikeProductList = PRODUCT_LIST_KEYS.stream()
                .anyMatch(item -> key.toLowerCase(Locale.ROOT).contains(item.toLowerCase(Locale.ROOT)));
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?>)) return false;
        Map<?, ?> firstMap = (Map<?, ?>) first;

        boolean itemLooksLikeProduct = firstMap.keySet().stream()
                .map(String::valueOf)
                .map(keyName -> keyName.toLowerCase(Locale.ROOT))
                .anyMatch(keyName -> keyName.contains("product")
                        || keyName.equals("name")
                        || keyName.equals("title")
                        || keyName.contains("price")
                        || keyName.contains("sales"));

        return keyLooksLikeProductList || itemLooksLikeProduct;
    }

    private void rerankProductList(List<Map<String, Object>> products, PreferenceContext context) {
        if (products.size() < 2) return;
        if (hasTrackedRecommendationOrder(products)) return;

        List<ScoredProduct> scored = new ArrayList<>();
        for (int index = 0; index < products.size(); index++) {
            Map<String, Object> product = products.get(index);
            String category = inferCategory(product);
            double originalScore = readScore(product);
            double categoryScore = categoryBoost(category, context);
            double finalScore = originalScore + categoryScore - index * 0.01;

            product.put("matchedPreferenceCategory", category);
            product.put("categoryPreferenceScore", round(categoryScore));
            product.put("rerankReason", buildReason(category, context));
            appendReason(product, buildReason(category, context));

            scored.add(new ScoredProduct(product, category, finalScore));
        }

        scored.sort(Comparator.comparingDouble(ScoredProduct::score).reversed());
        List<Map<String, Object>> reranked = enforceTopCategoryCoverage(scored, context);

        products.clear();
        products.addAll(reranked);
    }

    private boolean hasTrackedRecommendationOrder(List<Map<String, Object>> products) {
        for (Map<String, Object> product : products) {
            if (product == null) {
                continue;
            }
            Object token = product.get("recommendationToken");
            Object scene = product.get("recommendationScene");
            if (token != null && !String.valueOf(token).isBlank()
                    && scene != null && !String.valueOf(scene).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, Object>> enforceTopCategoryCoverage(List<ScoredProduct> scored, PreferenceContext context) {
        int inspectSize = Math.min(10, scored.size());
        int minPreferred = Math.min(6, inspectSize);

        List<ScoredProduct> preferred = new ArrayList<>();
        List<ScoredProduct> others = new ArrayList<>();
        Set<String> topTwo = new HashSet<>(context.topCategories.subList(0, Math.min(2, context.topCategories.size())));

        for (ScoredProduct product : scored) {
            if (topTwo.contains(product.category)) {
                preferred.add(product);
            } else {
                others.add(product);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int preferredIndex = 0;
        int otherIndex = 0;

        while (result.size() < inspectSize && preferredIndex < preferred.size() && result.size() < minPreferred) {
            result.add(preferred.get(preferredIndex++).product);
        }
        while (result.size() < inspectSize && otherIndex < others.size()) {
            result.add(others.get(otherIndex++).product);
        }
        while (result.size() < inspectSize && preferredIndex < preferred.size()) {
            result.add(preferred.get(preferredIndex++).product);
        }
        while (preferredIndex < preferred.size()) {
            result.add(preferred.get(preferredIndex++).product);
        }
        while (otherIndex < others.size()) {
            result.add(others.get(otherIndex++).product);
        }

        return result;
    }

    private double categoryBoost(String category, PreferenceContext context) {
        if (category == null || category.isBlank()) return -120;

        int index = context.topCategories.indexOf(category);
        double weight = context.categoryWeights.getOrDefault(category, 0.0);
        if (index == 0) return 1000 + weight * 4;
        if (index == 1) return 650 + weight * 3;
        if (index >= 0) return 350 + weight * 2;
        return -280;
    }

    private String buildReason(String category, PreferenceContext context) {
        if (category == null || category.isBlank()) {
            return "未命中主要偏好品类，仅作为少量探索推荐";
        }
        int index = context.topCategories.indexOf(category);
        if (index == 0) {
            return "命中用户 Top1 偏好品类：" + category;
        }
        if (index == 1) {
            return "命中用户 Top2 偏好品类：" + category;
        }
        if (index > 1) {
            return "命中用户历史偏好品类：" + category;
        }
        return "非主要偏好品类，仅保留少量多样性探索";
    }

    private void appendReason(Map<String, Object> product, String reason) {
        Object current = product.get("reason");
        if (current == null || String.valueOf(current).isBlank()) {
            product.put("reason", reason);
            return;
        }
        String text = String.valueOf(current);
        if (!text.contains(reason)) {
            product.put("reason", text + "；" + reason);
        }
    }

    private String inferCategory(Map<String, Object> product) {
        List<String> fields = List.of(
                "categoryName", "category", "categoryLabel", "productCategory",
                "name", "title", "productName", "subTitle", "description"
        );
        StringBuilder text = new StringBuilder();
        for (String field : fields) {
            Object value = product.get(field);
            if (value != null) {
                text.append(value).append(' ');
            }
        }

        String combined = text.toString();
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (combined.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return String.valueOf(product.getOrDefault("categoryName", ""));
    }

    private double readScore(Map<String, Object> product) {
        List<String> scoreKeys = List.of("finalScore", "hybridScore", "score", "rankScore", "weight", "sales");
        for (String key : scoreKeys) {
            Object value = product.get(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(String.valueOf(value));
                } catch (Exception ignored) {
                    // Try next score key.
                }
            }
        }
        return 0;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static class ScoredProduct {
        private final Map<String, Object> product;
        private final String category;
        private final double score;

        private ScoredProduct(Map<String, Object> product, String category, double score) {
            this.product = product;
            this.category = category;
            this.score = score;
        }

        private double score() {
            return score;
        }
    }

    private static class PreferenceContext {
        private final Map<String, Double> categoryWeights = new HashMap<>();
        private final List<String> topCategories = new ArrayList<>();

        static PreferenceContext from(Object body) {
            PreferenceContext context = new PreferenceContext();
            context.collect(body, new HashSet<>());
            context.normalize();
            return context;
        }

        @SuppressWarnings("unchecked")
        private void collect(Object node, Set<Integer> visited) {
            if (node == null) return;
            int identity = System.identityHashCode(node);
            if (visited.contains(identity)) return;
            visited.add(identity);

            if (node instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) node;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    if (CATEGORY_WEIGHT_KEYS.stream().anyMatch(item -> key.toLowerCase(Locale.ROOT).contains(item.toLowerCase(Locale.ROOT)))) {
                        readCategoryWeights(value);
                    }
                    collect(value, visited);
                }
            } else if (node instanceof List<?>) {
                List<?> list = (List<?>) node;
                for (Object item : list) {
                    collect(item, visited);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void readCategoryWeights(Object value) {
            if (value instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) value;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String category = normalizeCategory(String.valueOf(entry.getKey()));
                    Double weight = readNumber(entry.getValue());
                    if (category != null && weight != null) {
                        categoryWeights.merge(category, weight, Math::max);
                    }
                }
                return;
            }

            if (value instanceof List<?>) {
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    if (item instanceof String) {
                        String text = (String) item;
                        String category = normalizeCategory(text);
                        if (category != null) {
                            categoryWeights.merge(category, 1.0, Math::max);
                        }
                        continue;
                    }
                    if (item instanceof Map<?, ?>) {
                        Map<?, ?> map = (Map<?, ?>) item;
                        String category = firstText((Map<String, Object>) (Map<?, ?>) map,
                                List.of("category", "categoryName", "label", "name", "title"));
                        Double weight = firstNumber((Map<String, Object>) (Map<?, ?>) map,
                                List.of("weight", "score", "value", "ratio", "percent"));
                        category = normalizeCategory(category);
                        if (category != null) {
                            categoryWeights.merge(category, weight == null ? 1.0 : weight, Math::max);
                        }
                    }
                }
            }
        }

        private void normalize() {
            topCategories.clear();
            categoryWeights.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .forEach(entry -> topCategories.add(entry.getKey()));
        }

        private String normalizeCategory(String value) {
            if (value == null || value.isBlank()) return null;
            for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
                if (entry.getKey().equals(value)) {
                    return entry.getKey();
                }
                for (String keyword : entry.getValue()) {
                    if (value.contains(keyword)) {
                        return entry.getKey();
                    }
                }
            }
            return value;
        }

        private String firstText(Map<String, Object> map, List<String> keys) {
            for (String key : keys) {
                Object value = map.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value);
                }
            }
            return null;
        }

        private Double firstNumber(Map<String, Object> map, List<String> keys) {
            for (String key : keys) {
                Double value = readNumber(map.get(key));
                if (value != null) return value;
            }
            return null;
        }

        private Double readNumber(Object value) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value == null) return null;
            try {
                String text = String.valueOf(value).replace("%", "").trim();
                return Double.parseDouble(text);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
