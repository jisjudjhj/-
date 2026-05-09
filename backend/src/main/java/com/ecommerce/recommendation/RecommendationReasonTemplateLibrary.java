package com.ecommerce.recommendation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class RecommendationReasonTemplateLibrary {

    private static final String SCENE_PERSONAL = "personal";
    private static final String SCENE_GUESS_YOU_LIKE = "guess_you_like";
    private static final String SCENE_HOT = "hot";
    private static final String SCENE_SIMILAR = "similar";
    private static final String SOURCE_SNAPSHOT = "snapshot";

    private static final String REASON_TYPE_BEHAVIOR = "BEHAVIOR_MATCH";
    private static final String REASON_TYPE_HOT = "HOT_TREND";
    private static final String REASON_TYPE_SIMILAR = "SIMILARITY";
    private static final String REASON_TYPE_SNAPSHOT = "SNAPSHOT_PROFILE";
    private static final String REASON_TYPE_PRICE = "PRICE_BAND_MATCH";
    private static final String REASON_TYPE_BRAND = "BRAND_AFFINITY";
    private static final String REASON_TYPE_SHOP = "SHOP_AFFINITY";
    private static final String REASON_TYPE_PROMOTION = "PROMOTION_MATCH";
    private static final String REASON_TYPE_GENERAL = "GENERAL";

    private final Map<String, String> sceneBehaviorTemplates = new HashMap<>();
    private final Map<String, String> sceneDefaultTemplates = new HashMap<>();
    private final Map<String, String> scenePriceBandTemplates = new HashMap<>();
    private final Map<String, String> sceneBrandTemplates = new HashMap<>();
    private final Map<String, String> sceneShopTemplates = new HashMap<>();
    private final Map<String, String> scenePromotionTemplates = new HashMap<>();

    public RecommendationReasonTemplateLibrary() {
        sceneBehaviorTemplates.put("personal:view", "根据你最近浏览的「{category}」商品推荐");
        sceneBehaviorTemplates.put("personal:cart", "根据你最近加购的「{category}」商品推荐");
        sceneBehaviorTemplates.put("personal:favorite", "根据你最近收藏的「{category}」商品推荐");
        sceneBehaviorTemplates.put("personal:purchase", "结合你最近下单偏好，优先推荐「{category}」相关商品");
        sceneBehaviorTemplates.put("personal:search", "结合你最近搜索意图，匹配了「{category}」商品");

        sceneBehaviorTemplates.put("guess_you_like:view", "你最近常看「{category}」，猜你会喜欢这个");
        sceneBehaviorTemplates.put("guess_you_like:cart", "你最近加购较多「{category}」，猜你会继续关注");
        sceneBehaviorTemplates.put("guess_you_like:favorite", "你最近收藏偏向「{category}」，猜你会感兴趣");
        sceneBehaviorTemplates.put("guess_you_like:purchase", "你最近购买过同类「{category}」，猜你会复购或搭配购买");
        sceneBehaviorTemplates.put("guess_you_like:search", "根据你最近搜索内容，猜你会喜欢这类「{category}」");

        sceneDefaultTemplates.put(SCENE_PERSONAL, "根据你最近的浏览与加购偏好推荐");
        sceneDefaultTemplates.put(SCENE_GUESS_YOU_LIKE, "结合你最近行为偏好，为你筛选了更匹配的商品");
        sceneDefaultTemplates.put(SCENE_HOT, "该商品实时热度较高，近期浏览和下单都在增长");
        sceneDefaultTemplates.put(SCENE_SIMILAR, "和你刚看过的商品在风格与用途上更接近");

        scenePriceBandTemplates.put(SCENE_PERSONAL, "你近期常看{priceBand}价位商品，这款「{category}」更贴近你的预算");
        scenePriceBandTemplates.put(SCENE_GUESS_YOU_LIKE, "猜你会喜欢这款{priceBand}价位的「{category}」好物");

        sceneBrandTemplates.put(SCENE_PERSONAL, "你近期对品牌「{brand}」关注度较高，这件同品牌商品值得看看");
        sceneBrandTemplates.put(SCENE_GUESS_YOU_LIKE, "你近期常看「{brand}」相关商品，猜你会继续喜欢");

        sceneShopTemplates.put(SCENE_PERSONAL, "你近期在「{shop}」浏览较多，优先推荐同店铺优质商品");
        sceneShopTemplates.put(SCENE_GUESS_YOU_LIKE, "你最近常逛「{shop}」，这款同店新品可能更合你胃口");

        scenePromotionTemplates.put(SCENE_PERSONAL, "这件「{category}」正在参与{activity}，与你近期偏好匹配");
        scenePromotionTemplates.put(SCENE_GUESS_YOU_LIKE, "根据你的偏好，这件「{category}」{activity}商品更值得现在入手");
        scenePromotionTemplates.put(SCENE_HOT, "该商品近期{activity}热度上涨，浏览与下单同步增长");
    }

    public TemplateResult render(String scene,
                                 String behaviorType,
                                 String categoryName,
                                 String sourceType,
                                 boolean hotTrending) {
        return render(scene, behaviorType, categoryName, sourceType, hotTrending, Collections.emptyMap());
    }

    public TemplateResult render(String scene,
                                 String behaviorType,
                                 String categoryName,
                                 String sourceType,
                                 boolean hotTrending,
                                 Map<String, String> context) {
        String safeScene = normalize(scene);
        String safeBehavior = normalize(behaviorType);
        String safeCategory = StringUtils.hasText(categoryName) ? categoryName.trim() : "该类";
        Map<String, String> safeContext = context == null ? Collections.emptyMap() : context;
        String priceBand = trimToNull(safeContext.get("priceBand"));
        String brandName = trimToNull(safeContext.get("brand"));
        String shopName = trimToNull(safeContext.get("shop"));
        String activityTag = trimToNull(safeContext.get("activity"));

        if (SOURCE_SNAPSHOT.equalsIgnoreCase(normalize(sourceType))) {
            return new TemplateResult(REASON_TYPE_SNAPSHOT, "结合你的历史偏好与近期行为为你精选");
        }
        if (StringUtils.hasText(activityTag)) {
            String template = scenePromotionTemplates.get(safeScene);
            if (!StringUtils.hasText(template)) {
                template = scenePromotionTemplates.get(SCENE_GUESS_YOU_LIKE);
            }
            if (StringUtils.hasText(template)) {
                return new TemplateResult(REASON_TYPE_PROMOTION, renderTemplate(template, safeCategory, null, null, activityTag));
            }
        }
        if (StringUtils.hasText(brandName)) {
            String template = sceneBrandTemplates.get(safeScene);
            if (!StringUtils.hasText(template)) {
                template = sceneBrandTemplates.get(SCENE_GUESS_YOU_LIKE);
            }
            if (StringUtils.hasText(template)) {
                return new TemplateResult(REASON_TYPE_BRAND, renderTemplate(template, safeCategory, null, brandName, null));
            }
        }
        if (StringUtils.hasText(shopName)) {
            String template = sceneShopTemplates.get(safeScene);
            if (!StringUtils.hasText(template)) {
                template = sceneShopTemplates.get(SCENE_GUESS_YOU_LIKE);
            }
            if (StringUtils.hasText(template)) {
                return new TemplateResult(REASON_TYPE_SHOP, renderTemplate(template, safeCategory, shopName, null, null));
            }
        }
        if (StringUtils.hasText(priceBand)) {
            String template = scenePriceBandTemplates.get(safeScene);
            if (!StringUtils.hasText(template)) {
                template = scenePriceBandTemplates.get(SCENE_GUESS_YOU_LIKE);
            }
            if (StringUtils.hasText(template)) {
                return new TemplateResult(REASON_TYPE_PRICE, renderTemplate(template, safeCategory, null, null, null).replace("{priceBand}", priceBand));
            }
        }
        if (SCENE_HOT.equals(safeScene)) {
            String text = hotTrending
                    ? "该商品实时热度较高，近期浏览和下单都在增长"
                    : sceneDefaultTemplates.get(SCENE_HOT);
            return new TemplateResult(REASON_TYPE_HOT, text);
        }
        if (SCENE_SIMILAR.equals(safeScene)) {
            return new TemplateResult(REASON_TYPE_SIMILAR, sceneDefaultTemplates.get(SCENE_SIMILAR));
        }

        String template = sceneBehaviorTemplates.get(safeScene + ":" + safeBehavior);
        if (!StringUtils.hasText(template)) {
            template = sceneDefaultTemplates.get(safeScene);
        }
        if (!StringUtils.hasText(template)) {
            template = "根据你最近行为偏好推荐";
        }
        String text = renderTemplate(template, safeCategory, shopName, brandName, activityTag);
        String reasonType = StringUtils.hasText(sceneBehaviorTemplates.get(safeScene + ":" + safeBehavior))
                ? REASON_TYPE_BEHAVIOR
                : REASON_TYPE_GENERAL;
        return new TemplateResult(reasonType, text);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String renderTemplate(String template,
                                  String category,
                                  String shop,
                                  String brand,
                                  String activity) {
        String rendered = template == null ? "" : template;
        rendered = rendered.replace("{category}", StringUtils.hasText(category) ? category : "该类");
        rendered = rendered.replace("{shop}", StringUtils.hasText(shop) ? shop : "该店铺");
        rendered = rendered.replace("{brand}", StringUtils.hasText(brand) ? brand : "该品牌");
        rendered = rendered.replace("{activity}", StringUtils.hasText(activity) ? activity : "活动");
        return rendered;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public static class TemplateResult {
        private final String reasonType;
        private final String reasonText;

        public TemplateResult(String reasonType, String reasonText) {
            this.reasonType = reasonType;
            this.reasonText = reasonText;
        }

        public String getReasonType() {
            return reasonType;
        }

        public String getReasonText() {
            return reasonText;
        }
    }
}
