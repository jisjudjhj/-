package com.ecommerce.recommendation;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.StreamUserCategoryPreference;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐解释生成器
 *
 * 为每个推荐结果生成人类可读的解释，说明"为什么推荐这个商品"
 *
 * 解释类型:
 * - CF: "和你兴趣相似的用户也喜欢"
 * - CB: "根据你浏览过的XX类商品推荐"
 * - HOT: "热门畅销商品"
 * - TAG: "你可能喜欢XX标签的商品"
 * - CATEGORY: "你经常浏览XX品类"
 */
@Component
public class RecommendationExplainer {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserBehaviorMapper behaviorMapper;

    @Autowired
    private CollaborativeFiltering collaborativeFiltering;

    @Autowired
    private ContentBasedFiltering contentBasedFiltering;

    @Autowired
    private RecommendationRealtimeCacheService recommendationRealtimeCacheService;

    public enum ReasonType {
        COLLABORATIVE("和你兴趣相似的用户也在看"),
        CONTENT_TAG("根据你喜欢的「%s」标签推荐"),
        CONTENT_CATEGORY("你经常浏览的%s品类"),
        SIMILAR_PRODUCT("与你看过的「%s」相似"),
        HOT_SELLING("热门畅销商品，已售%d件"),
        HIGH_RATING("好评如潮，评分%.1f"),
        NEW_ARRIVAL("新品上架"),
        COLD_START("少量多样性探索推荐");

        private final String template;

        ReasonType(String template) {
            this.template = template;
        }

        public String getTemplate() {
            return template;
        }
    }

    /**
     * 为推荐的商品列表生成解释
     *
     * 优化点：批量加载商品，消除 N+1 查询问题；
     * 延迟构建 userVector（仅在确实不需要时跳过）
     */
    public List<Map<String, Object>> explain(Long userId, List<Long> productIds,
                                              List<Long> cfResults,
                                              List<Long> cbResults,
                                              List<Long> hotResults) {
        List<Map<String, Object>> explanations = new ArrayList<>();

        Set<Long> cfSet = new HashSet<>(cfResults != null ? cfResults : Collections.emptyList());
        Set<Long> cbSet = new HashSet<>(cbResults != null ? cbResults : Collections.emptyList());
        Set<Long> hotSet = new HashSet<>(hotResults != null ? hotResults : Collections.emptyList());

        List<Map<String, Object>> preferences = behaviorMapper.selectUserPreferences(userId);
        Set<String> userTags = extractUserTags(preferences);
        List<String> realtimePreferredCategories = loadRealtimePreferredCategories(userId);

        Map<Long, Product> productMap = new HashMap<>();
        List<Long> validIds = productIds.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (!validIds.isEmpty()) {
            List<Product> products = productMapper.selectBatchIds(validIds);
            for (Product p : products) {
                productMap.put(p.getId(), p);
            }
        }

        Map<Long, String> categoryNameMap = new HashMap<>();
        try {
            List<Map<String, Object>> cats = productMapper.selectAllCategoryIds();
            for (Map<String, Object> cat : cats) {
                Object cid = cat.get("categoryId");
                Object cname = cat.get("categoryName");
                if (cid != null && cname != null) {
                    categoryNameMap.put(((Number) cid).longValue(), cname.toString());
                }
            }
        } catch (Exception ignored) {}

        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            if (product == null) continue;

            Map<String, Object> explanation = new LinkedHashMap<>();
            explanation.put("productId", productId);

            List<String> reasons = new ArrayList<>();
            String primaryReason;

            if (cfSet.contains(productId)) {
                primaryReason = ReasonType.COLLABORATIVE.name();
                reasons.add("和你口味相似的人也在买");
            } else if (cbSet.contains(productId)) {
                Set<String> productTags = parseTags(
                        product.getTags() != null ? product.getTags().toString() : "");
                Set<String> commonTags = new HashSet<>(userTags);
                commonTags.retainAll(productTags);

                if (!commonTags.isEmpty()) {
                    String tag = commonTags.iterator().next();
                    primaryReason = ReasonType.CONTENT_TAG.name();
                    reasons.add(String.format("因为你喜欢「%s」", tag));
                } else if (product.getCategoryId() != null) {
                    String catName = categoryNameMap.get(product.getCategoryId());
                    primaryReason = ReasonType.CONTENT_CATEGORY.name();
                    if (catName != null && realtimePreferredCategories.contains(catName)) {
                        reasons.add(String.format("根据你最近在%s的行为偏好推荐", catName));
                    } else {
                        reasons.add(catName != null
                                ? String.format("你常逛的%s频道精选", catName)
                                : "根据你的偏好推荐");
                    }
                } else {
                    primaryReason = ReasonType.CONTENT_CATEGORY.name();
                    reasons.add("根据你的偏好推荐");
                }
            } else if (hotSet.contains(productId)) {
                primaryReason = ReasonType.HOT_SELLING.name();
                int sales = product.getSalesCount() != null ? product.getSalesCount() : 0;
                if (sales > 10000) {
                    reasons.add(String.format("爆款热销 %d+人已购", sales));
                } else if (sales > 100) {
                    reasons.add(String.format("热销好物 已售%d件", sales));
                } else {
                    reasons.add(String.format("热门候选补充，当前真实销量%d件", sales));
                }
            } else {
                primaryReason = ReasonType.COLD_START.name();
                if (product.getRating() != null && product.getRating().doubleValue() >= 4.7) {
                    reasons.add(String.format("作为少量多样性探索推荐，评分%.1f", product.getRating().doubleValue()));
                } else {
                    reasons.add("作为少量多样性探索推荐");
                }
            }

            if (product.getRating() != null && product.getRating().doubleValue() >= 4.5) {
                reasons.add(String.format("好评%.1f分", product.getRating().doubleValue()));
            }

            explanation.put("primaryReason", primaryReason);
            explanation.put("reasons", reasons);
            explanation.put("reasonText", String.join(" · ", reasons));
            explanations.add(explanation);
        }

        return explanations;
    }

    /**
     * 为相似商品生成解释
     */
    public Map<String, Object> explainSimilar(Long sourceProductId, Long targetProductId) {
        Product source = productMapper.selectById(sourceProductId);
        Product target = productMapper.selectById(targetProductId);

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("productId", targetProductId);

        List<String> reasons = new ArrayList<>();

        if (source != null && target != null) {
            Set<String> sourceTags = parseTags(
                    source.getTags() != null ? source.getTags().toString() : "");
            Set<String> targetTags = parseTags(
                    target.getTags() != null ? target.getTags().toString() : "");

            Set<String> commonTags = new HashSet<>(sourceTags);
            commonTags.retainAll(targetTags);

            if (!commonTags.isEmpty()) {
                reasons.add(String.format("与「%s」相似", source.getName()));
                reasons.add(String.format("共同特征: %s", String.join("、", commonTags)));
            } else {
                reasons.add(String.format("与「%s」同类商品", source.getName()));
            }

            double tagSim = contentBasedFiltering.jaccardSimilarity(sourceTags, targetTags);
            explanation.put("similarity", String.format("%.2f", tagSim));
        }

        explanation.put("reasons", reasons);
        explanation.put("reasonText", String.join(" · ", reasons));
        return explanation;
    }

    private Set<String> extractUserTags(List<Map<String, Object>> preferences) {
        Set<String> tags = new HashSet<>();
        for (Map<String, Object> pref : preferences) {
            Object tagObj = pref.get("tags");
            if (tagObj != null) {
                tags.addAll(parseTags(tagObj.toString()));
            }
        }
        return tags;
    }

    private List<String> loadRealtimePreferredCategories(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        List<StreamUserCategoryPreference> rows = recommendationRealtimeCacheService.getUserPreferenceRows(userId, 5);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> categories = new ArrayList<>();
        for (StreamUserCategoryPreference row : rows) {
            if (row != null && row.getCategoryName() != null && !row.getCategoryName().trim().isEmpty()) {
                categories.add(row.getCategoryName().trim());
            }
        }
        return categories;
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
}
