package com.ecommerce.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.BusinessException;
import com.ecommerce.dto.ShoppingIntentDTO;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductReview;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.ProductReviewMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.service.AiService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.RecommendationService;
import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private static final String REVIEW_SUMMARY_CACHE_PREFIX = "ai:review_summary:";
    private static final String PROMPT_CATEGORY_SUMMARY_CACHE_KEY = "ai:prompt:category_summary";
    private static final String PROMPT_PRODUCT_SUMMARY_CACHE_KEY = "ai:prompt:product_summary";
    private static final String CATEGORY_NAME_MAP_CACHE_KEY = "ai:category_name_map";
    private static final int REVIEW_SUMMARY_CACHE_HOURS = 2;
    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final int GUIDE_RESULT_LIMIT = 3;
    private static final int PERSONALIZED_HINT_LIMIT = 18;
    private static final int PROMPT_USER_CATEGORY_LIMIT = 4;
    private static final int PROMPT_USER_TAG_LIMIT = 8;
    private static final int MERCHANT_COPY_TAG_LIMIT = 6;
    private static final int MERCHANT_ACTION_LIMIT = 4;
    private static final int INTENT_ANALYSIS_MAX_TOKENS = 400;
    private static final int INTENT_KEYWORD_LIMIT = 6;
    private static final int INTENT_BRAND_LIMIT = 4;
    private static final int INTENT_SCENE_LIMIT = 4;
    private static final Set<String> VALID_INTENT_MESSAGE_TYPES = new LinkedHashSet<>(Arrays.asList(
            "greeting",
            "thanks",
            "farewell",
            "general_chat",
            "shopping_chat",
            "recommendation",
            "product_question",
            "product_comparison"
    ));
    private static final double INTENT_ANALYSIS_TEMPERATURE = 0.1d;
    private static final List<String> MERCHANT_COPY_HINTS = Arrays.asList(
            "文案", "上架", "标题", "卖点", "描述", "详情", "种草", "口播", "直播", "海报", "客服回复", "话术"
    );
    private static final List<String> MERCHANT_OPERATE_HINTS = Arrays.asList(
            "怎么卖", "怎么推", "怎么写", "怎么发", "活动", "促销", "转化", "点击", "曝光", "上新", "优化"
    );
    private static final String CONNECTIVITY_TEST_SYSTEM_PROMPT =
            "你是电商系统的 AI 连通性测试助手。"
                    + "请严格按照用户要求直接输出结果，不要解释，不要补充说明，不要加前后缀，不要换行。";
    private static final int CUSTOMER_SUPPORT_HISTORY_LIMIT = 16;
    private static final int CUSTOMER_SUPPORT_TRANSCRIPT_LIMIT = 32;
    private static final int CUSTOMER_SUPPORT_REPLY_MAX_TOKENS = 420;
    private static final int CUSTOMER_SUPPORT_SUMMARY_MAX_TOKENS = 700;
    private static final List<String> RECOMMENDATION_HINTS = Arrays.asList(
            "推荐", "想买", "买", "挑", "选", "求推荐", "帮我看看", "有没有",
            "预算", "以内", "以下", "左右", "适合", "哪款", "哪个好", "送人", "自用",
            "备选", "换一批", "再来几个", "排序", "销量", "品牌"
    );
    private static final List<String> SCENE_KEYWORDS = Arrays.asList(
            "通勤", "办公", "学生", "上课", "宿舍", "家用", "旅行", "出差",
            "游戏", "运动", "跑步", "健身", "送人", "自用", "儿童", "婴儿"
    );
    private static final List<String> FEATURE_KEYWORDS = Arrays.asList(
            "蓝牙", "无线", "有线", "降噪", "音质", "续航", "轻薄", "快充", "拍照",
            "护眼", "高刷", "大屏", "便携", "保湿", "补水", "美白", "控油", "敏感肌",
            "高蛋白", "零食", "阅读", "书写", "收纳", "保温", "静音", "头戴", "入耳", "真无线"
    );
    private static final List<String> BRAND_KEYWORDS = Arrays.asList(
            "苹果", "apple", "airpods", "华为", "小米", "oppo", "vivo", "荣耀", "一加",
            "三星", "samsung", "索尼", "sony", "联想", "lenovo", "戴尔", "dell", "惠普", "hp",
            "华硕", "asus", "耐克", "nike", "阿迪达斯", "adidas", "李宁", "安踏", "蒙牛", "伊利",
            "雅诗兰黛", "兰蔻", "欧莱雅", "完美日记"
    );
    private static final List<String> HIGH_SALES_HINTS = Arrays.asList(
            "销量高", "销量更高", "高销量", "按销量", "热销", "热门", "爆款", "卖得好"
    );
    private static final List<String> MAJOR_BRAND_HINTS = Arrays.asList(
            "大品牌", "品牌优先", "知名品牌", "主流品牌", "牌子硬", "大牌"
    );
    private static final List<String> ALTERNATIVE_HINTS = Arrays.asList(
            "备选", "换一批", "换几个", "再来几个", "别的款", "其他款", "更多选择", "再给我"
    );
    private static final List<String> LONG_TERM_USE_HINTS = Arrays.asList(
            "长期用", "长期使用", "久用", "耐用", "更稳", "更省心", "更值得长期用"
    );
    private static final List<String> MAJOR_BRAND_KEYWORDS = Arrays.asList(
            "苹果", "apple", "华为", "小米", "荣耀", "oppo", "vivo", "三星", "samsung",
            "索尼", "sony", "联想", "lenovo", "戴尔", "dell", "惠普", "hp", "华硕", "asus",
            "耐克", "nike", "阿迪达斯", "adidas", "雅诗兰黛", "兰蔻", "欧莱雅"
    );
    private static final List<String> LONG_TERM_USE_FEATURES = Arrays.asList(
            "续航", "耐用", "稳定", "静音", "护眼", "保温", "便携", "轻薄", "快充"
    );

    private static final List<String> GREETING_HINTS = Arrays.asList(
            "你好", "您好", "嗨", "哈喽", "hello", "hi", "在吗"
    );
    private static final List<String> THANKS_HINTS = Arrays.asList(
            "谢谢", "多谢", "感谢", "thanks", "thankyou", "thank you"
    );
    private static final List<String> FAREWELL_HINTS = Arrays.asList(
            "再见", "拜拜", "bye", "回头聊", "下次见"
    );
    private static final List<String> COMPARISON_HINTS = Arrays.asList(
            "比较", "对比", "区别", "差别", "哪个好", "怎么选", "选哪个", "谁更", "和"
    );
    private static final List<String> PRODUCT_QUESTION_HINTS = Arrays.asList(
            "怎么样", "好不好", "可以吗", "适合", "支持", "有吗", "值得", "推荐吗", "续航",
            "降噪", "音质", "参数", "价格", "多少钱", "评分", "销量", "优点", "缺点"
    );
    private static final List<String> GENERAL_SHOPPING_HINTS = Arrays.asList(
            "怎么买", "怎么挑", "怎么选", "区别", "差别", "适合什么", "有什么用", "值不值", "推荐理由"
    );
    private static final List<String> SALES_PRIORITY_HINTS = Arrays.asList(
            "销量", "热销", "卖得好", "销量高", "高销量", "成交高"
    );
    private static final List<String> BIG_BRAND_PRIORITY_HINTS = Arrays.asList(
            "大品牌", "大牌", "品牌优先", "大厂", "主流品牌"
    );
    private static final List<String> ALTERNATIVE_PRIORITY_HINTS = Arrays.asList(
            "备选", "换一批", "换一组", "其他款", "别的", "其它", "再给我"
    );
    private static final List<String> LONG_TERM_PRIORITY_HINTS = Arrays.asList(
            "长期用", "长期使用", "耐用", "用得久", "稳定", "久用"
    );
    private static final List<String> LONG_TERM_PRODUCT_HINTS = Arrays.asList(
            "续航", "耐用", "稳定", "做工", "保修", "质保", "旗舰"
    );
    private static final List<String> MAINSTREAM_BRAND_HINTS = Arrays.asList(
            "苹果", "apple", "华为", "小米", "oppo", "vivo", "荣耀",
            "三星", "samsung", "索尼", "sony", "联想", "lenovo",
            "戴尔", "dell", "惠普", "hp", "华硕", "asus"
    );

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    @Value("${ai.max-tokens:1024}")
    private int maxTokens;

    @Value("${ai.temperature:0.7}")
    private double temperature;

    @Value("${ai.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${ai.read-timeout-ms:65000}")
    private int readTimeoutMs;

    @Value("${ai.prompt-cache-minutes:30}")
    private int promptCacheMinutes;

    @Value("${ai.prompt-product-limit:20}")
    private int promptProductLimit;

    @Value("${ai.test-max-tokens:64}")
    private int testMaxTokens;

    @Value("${ai.test-temperature:0.2}")
    private double testTemperature;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductReviewMapper reviewMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private UserBehaviorMapper behaviorMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private RedisUtil redisUtil;

    private RestTemplate restTemplate;

    @PostConstruct
    public void initRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
        log.info("[AI] RestTemplate 初始化完成，connectTimeout={}ms, readTimeout={}ms",
                connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public String connectivityTest(String message) {
        String userMessage = trimToNull(message);
        if (userMessage == null) {
            userMessage = "请回复：连接成功";
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msgOf("system", CONNECTIVITY_TEST_SYSTEM_PROMPT));
        messages.add(msgOf("user", userMessage));
        return callChatCompletion(messages, Math.min(testMaxTokens, maxTokens), testTemperature);
    }

    // ==================== 购物助手 ====================

    @Override
    public Map<String, Object> shoppingAssistant(Long userId, String userMessage, List<Map<String, String>> history) {
        String identityReply = resolveIdentityReply(userMessage);
        if (identityReply != null) {
            return buildAssistantResult(identityReply, Collections.emptyList(), null);
        }

        ShoppingIntentDTO intent = parseShoppingIntent(userMessage, history);
        enrichIntentWithRealtimeProfile(userId, intent);
        Map<Long, String> categoryNames = loadCategoryNameMap();
        List<Product> activeProducts = loadActiveProductsForGuide();
        List<Product> mentionedProducts = findMentionedProducts(activeProducts, categoryNames, intent);
        resolveMessageType(intent, userMessage, mentionedProducts);

        String conversationalReply = resolveSalesConversationalReply(userMessage, intent);
        if (conversationalReply != null) {
            return buildAssistantResult(conversationalReply, Collections.emptyList(), intent);
        }

        if ("product_comparison".equals(intent.getMessageType())) {
            return buildSalesComparisonResponse(intent, mentionedProducts, categoryNames);
        }

        if ("product_question".equals(intent.getMessageType())) {
            return buildSalesProductQuestionResponse(intent, mentionedProducts, categoryNames);
        }

        if (intent.isRecommendationMode()) {
            return buildGuidedShoppingResponse(userId, intent);
        }

        String systemPrompt = buildShoppingSystemPrompt(userId, intent);

        List<Map<String, String>> sanitizedHistory = sanitizeHistory(history, userMessage);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msgOf("system", systemPrompt));
        messages.addAll(sanitizedHistory);
        messages.add(msgOf("user", userMessage));

        log.info("[AI] 购物助手请求 userId={}, userMessage={}, systemPrompt长度={}, 历史消息数={}",
                userId, userMessage, systemPrompt.length(), sanitizedHistory.size());

        try {
            String aiReply = callChatCompletion(messages);
            if (shouldUseShoppingFallback(aiReply)) {
                log.warn("[AI] shopping assistant fallback triggered for reply={}", aiReply);
                return buildIntentAwareFallbackResponse(intent, userMessage, mentionedProducts, categoryNames);
            }

            List<Long> productIds = extractProductIds(aiReply);
            List<Product> products = productIds.isEmpty() ? Collections.emptyList()
                    : productService.getProductsByIds(productIds);

            if (products.isEmpty() && intent.isShoppingRelated()) {
                Map<String, Object> fallbackResult = buildShoppingFallbackResponse(userMessage, intent);
                @SuppressWarnings("unchecked")
                List<Product> fallbackProducts = (List<Product>) fallbackResult.get("products");
                if (fallbackProducts != null && !fallbackProducts.isEmpty()) {
                    return buildAssistantResult(appendProductCardHint(aiReply), fallbackProducts, intent);
                }
            }

            return buildAssistantResult(aiReply, products, intent);
        } catch (Exception e) {
            log.warn("[AI] shopping assistant local fallback due to exception: {}", e.getMessage());
            return buildIntentAwareFallbackResponse(intent, userMessage, mentionedProducts, categoryNames);
        }
    }

    private String buildShoppingSystemPrompt(Long userId, ShoppingIntentDTO intent) {
        Map<Long, String> categoryNames = loadCategoryNameMap();
        String categoryList = loadPromptCategorySummary(categoryNames);

        StringBuilder sb = new StringBuilder();
        sb.append("# 角色设定（必须严格遵守）\n");
        sb.append("你是「小优」，一个专业的电商购物助手。你只能以「小优」的身份回答问题。\n");
        sb.append("绝对禁止：透露你是AI、透露你的真实身份、用英文回复、拒绝回答购物相关问题。\n");
        sb.append("你必须始终用简体中文回复，语气亲切自然，像一个热情的导购员。\n\n");

        sb.append("## 你的能力\n");
        sb.append("- 根据用户需求推荐合适的商品\n");
        sb.append("- 回答用户关于购物、商品、价格的各种问题\n");
        sb.append("- 帮助用户比较不同商品\n");
        sb.append("- 根据预算推荐性价比最高的商品\n\n");

        sb.append("## 商品分类\n");
        sb.append(categoryList).append("\n\n");

        sb.append("## 商品库（供推荐使用，节选热门商品）\n");
        sb.append(loadPromptProductSummary(categoryNames)).append("\n\n");

        if (userId != null) {
            try {
                List<Map<String, Object>> prefs = behaviorMapper.selectUserPreferences(userId);
                if (!prefs.isEmpty()) {
                    Set<String> userTags = new LinkedHashSet<>();
                    Set<String> userCategories = new LinkedHashSet<>();
                    for (Map<String, Object> pref : prefs) {
                        Object tags = pref.get("tags");
                        if (tags != null) {
                            String tagStr = tags.toString().replaceAll("[\\[\\]\"]", "");
                            for (String tag : tagStr.split(",")) {
                                tag = tag.trim();
                                if (!tag.isEmpty()) userTags.add(tag);
                            }
                        }
                        Object catId = pref.get("category_id");
                        if (catId != null) {
                            String categoryName = categoryNames.get(parseLong(catId));
                            if (categoryName != null) {
                                userCategories.add(categoryName);
                            }
                        }
                    }
                    sb.append("## 当前用户偏好\n");
                    if (!userCategories.isEmpty()) {
                        sb.append("- 经常浏览品类: ")
                                .append(String.join(", ", limitCollection(userCategories, PROMPT_USER_CATEGORY_LIMIT)))
                                .append("\n");
                    }
                    if (!userTags.isEmpty()) {
                        sb.append("- 感兴趣的标签: ")
                                .append(String.join(", ", limitCollection(userTags, PROMPT_USER_TAG_LIMIT)))
                                .append("\n");
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                log.warn("[AI] 获取用户偏好失败: {}", e.getMessage());
            }
        }

        Map<String, Object> personaPayload = buildIntentPersonaPayload(intent);
        if (!personaPayload.isEmpty()) {
            sb.append("## 当前用户实时画像（用于默认承接，不得覆盖用户本轮明确需求）\n");
            if (personaPayload.get("segmentName") != null) {
                sb.append("- 当前分群: ").append(personaPayload.get("segmentName")).append("\n");
            }
            if (personaPayload.get("summary") != null) {
                sb.append("- 画像摘要: ").append(personaPayload.get("summary")).append("\n");
            }
            List<String> topCategories = trimAndLimitList(toStringList(personaPayload.get("topCategories")), 4);
            if (!topCategories.isEmpty()) {
                sb.append("- 偏好品类: ").append(String.join("、", topCategories)).append("\n");
            }
            List<String> topTags = trimAndLimitList(toStringList(personaPayload.get("topTags")), 6);
            if (!topTags.isEmpty()) {
                sb.append("- 偏好标签: ").append(String.join("、", topTags)).append("\n");
            }
            if (personaPayload.get("strategyHint") != null) {
                sb.append("- 承接方式: ").append(personaPayload.get("strategyHint")).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 推荐商品时的规则\n");
        sb.append("1. 只推荐上面商品库中存在的商品，不要编造商品\n");
        sb.append("2. 推荐时使用这种格式引用商品: [商品名称](product:商品ID)\n");
        sb.append("3. 每次推荐3-5个商品\n");
        sb.append("4. 简要说明推荐理由（性价比、销量、评分、用户偏好匹配度等）\n");
        sb.append("5. 如果用户提了预算，优先推荐该预算范围内的商品\n");
        sb.append("6. 如果需求不明确，优先结合当前用户画像追问或承接，不要反复默认推荐耳机、数码\n");
        sb.append("7. 如果用户没有明确品类，可优先从画像高权重品类中给出1-2个承接方向\n");
        sb.append("8. 话术要和用户当前画像一致，例如高意向用户更直接给候选，画像较轻用户先收窄需求\n");
        sb.append("9. 始终使用简体中文回复\n");

        return sb.toString();
    }

    private Map<String, Object> buildAssistantResult(String reply, List<Product> products, ShoppingIntentDTO intent) {
        List<Product> safeProducts = products == null ? Collections.emptyList() : products;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("products", safeProducts);
        result.put("assistantVersion", "shopping-copilot-v4");
        if (intent != null) {
            result.put("intent", intent);
            result.put("analysisMode", firstNonEmpty(intent.getAnalysisSource(), "rule"));
            result.put("needClarification", intent.isNeedClarification());
            if (trimToNull(intent.getClarificationQuestion()) != null) {
                result.put("clarificationQuestion", intent.getClarificationQuestion());
            }
            result.put("suggestedPrompts", buildUserSuggestedPrompts(intent, safeProducts));
            Map<String, Object> shoppingBrief = buildShoppingBrief(intent, safeProducts);
            if (!shoppingBrief.isEmpty()) {
                result.put("shoppingBrief", shoppingBrief);
            }
            String strategyLabel = buildStrategyLabel(intent, safeProducts);
            if (trimToNull(strategyLabel) != null) {
                result.put("strategyLabel", strategyLabel);
            }
            Map<String, Object> personaCard = buildPersonaCard(intent);
            if (!personaCard.isEmpty()) {
                result.put("personaCard", personaCard);
            }
            List<Map<String, Object>> insightCards = buildInsightCards(intent, safeProducts);
            if (!insightCards.isEmpty()) {
                result.put("insightCards", insightCards);
            }
            List<Map<String, Object>> nextActions = buildNextActions(intent, safeProducts);
            if (!nextActions.isEmpty()) {
                result.put("nextActions", nextActions);
            }
            Map<String, Object> recommendationQuality = buildRecommendationQualityProfile(safeProducts);
            if (!recommendationQuality.isEmpty()) {
                result.put("recommendationQuality", recommendationQuality);
            }
        }
        return result;
    }

    private Map<String, Object> buildRecommendationQualityProfile(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> categoryCounter = new LinkedHashMap<>();
        Map<String, Integer> merchantCounter = new LinkedHashMap<>();
        Set<String> reasonSet = new LinkedHashSet<>();
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        BigDecimal sumPrice = BigDecimal.ZERO;
        int pricedCount = 0;

        for (Product product : products) {
            if (product == null) {
                continue;
            }
            if (StringUtils.hasText(product.getCategoryName())) {
                categoryCounter.merge(product.getCategoryName().trim(), 1, Integer::sum);
            } else if (product.getCategoryId() != null) {
                categoryCounter.merge("category#" + product.getCategoryId(), 1, Integer::sum);
            }
            if (StringUtils.hasText(product.getMerchantName())) {
                merchantCounter.merge(product.getMerchantName().trim(), 1, Integer::sum);
            } else if (product.getMerchantId() != null) {
                merchantCounter.merge("merchant#" + product.getMerchantId(), 1, Integer::sum);
            }
            if (StringUtils.hasText(product.getRecommendReason())) {
                reasonSet.add(product.getRecommendReason().trim());
            }

            if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) >= 0) {
                BigDecimal price = product.getPrice();
                minPrice = minPrice == null ? price : minPrice.min(price);
                maxPrice = maxPrice == null ? price : maxPrice.max(price);
                sumPrice = sumPrice.add(price);
                pricedCount++;
            }
        }

        int total = Math.max(1, products.size());
        double categoryCoverage = Math.min(1D, (double) categoryCounter.size() / Math.min(total, 5));
        double merchantCoverage = Math.min(1D, (double) merchantCounter.size() / Math.min(total, 5));
        double reasonCoverage = Math.min(1D, (double) reasonSet.size() / Math.min(total, 4));
        double priceCoverage = Math.min(1D, (double) pricedCount / Math.min(total, 5));

        double categoryEntropy = normalizedEntropy(categoryCounter);
        double merchantEntropy = normalizedEntropy(merchantCounter);
        double diversityScore = (categoryEntropy * 0.55 + merchantEntropy * 0.45) * 100D;
        double categoryTopShare = computeTopShare(categoryCounter, total);
        double merchantTopShare = computeTopShare(merchantCounter, total);

        BigDecimal avgPrice = pricedCount <= 0
                ? BigDecimal.ZERO
                : sumPrice.divide(BigDecimal.valueOf(pricedCount), 2, RoundingMode.HALF_UP);
        double priceSpanRate = (pricedCount <= 0 || minPrice == null || maxPrice == null || avgPrice.compareTo(BigDecimal.ZERO) <= 0)
                ? 0D
                : maxPrice.subtract(minPrice)
                .divide(avgPrice, 4, RoundingMode.HALF_UP)
                .doubleValue();
        double priceSpanScore = Math.min(1D, priceSpanRate / 0.9D);

        double baseQualityScore = (categoryCoverage * 0.24
                + merchantCoverage * 0.18
                + reasonCoverage * 0.16
                + (diversityScore / 100D) * 0.27
                + Math.max(priceCoverage, priceSpanScore) * 0.15) * 100D;
        double concentrationPenalty = ((Math.max(0D, categoryTopShare - 0.55D)
                + Math.max(0D, merchantTopShare - 0.55D)) * 35D);
        double qualityScore = clampScore(baseQualityScore - concentrationPenalty, 0D, 100D);

        List<String> riskFlags = buildQualityRiskFlags(
                categoryTopShare, merchantTopShare, reasonCoverage, categoryCounter.size(), merchantCounter.size(), priceSpanRate);
        List<String> optimizationActions = buildQualityOptimizationActions(riskFlags, total);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("score", roundDouble(qualityScore, 2));
        profile.put("categoryCoverage", roundDouble(categoryCoverage * 100D, 2));
        profile.put("merchantCoverage", roundDouble(merchantCoverage * 100D, 2));
        profile.put("reasonCoverage", roundDouble(reasonCoverage * 100D, 2));
        profile.put("categoryDiversity", roundDouble(categoryEntropy * 100D, 2));
        profile.put("merchantDiversity", roundDouble(merchantEntropy * 100D, 2));
        profile.put("categoryTopShare", roundDouble(categoryTopShare * 100D, 2));
        profile.put("merchantTopShare", roundDouble(merchantTopShare * 100D, 2));
        profile.put("categoryCount", categoryCounter.size());
        profile.put("merchantCount", merchantCounter.size());
        profile.put("pricedCount", pricedCount);
        profile.put("priceRange", buildPriceRangePayload(minPrice, maxPrice, sumPrice, pricedCount));
        profile.put("riskFlags", riskFlags);
        profile.put("optimizationActions", optimizationActions);
        profile.put("evaluationVersion", "quality-profile-v2");
        profile.put("qualityLevel", resolveQualityLevel(qualityScore));
        return profile;
    }

    private Map<String, Object> buildPriceRangePayload(BigDecimal minPrice,
                                                       BigDecimal maxPrice,
                                                       BigDecimal sumPrice,
                                                       int pricedCount) {
        if (pricedCount <= 0 || minPrice == null || maxPrice == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> priceRange = new LinkedHashMap<>();
        BigDecimal avgPrice = sumPrice.divide(BigDecimal.valueOf(pricedCount), 2, RoundingMode.HALF_UP);
        BigDecimal span = maxPrice.subtract(minPrice).max(BigDecimal.ZERO);
        BigDecimal spanRate = avgPrice.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : span.divide(avgPrice, 4, RoundingMode.HALF_UP);
        priceRange.put("min", minPrice);
        priceRange.put("max", maxPrice);
        priceRange.put("avg", avgPrice);
        priceRange.put("span", span);
        priceRange.put("spanRate", spanRate);
        return priceRange;
    }

    private String resolveQualityLevel(double qualityScore) {
        if (qualityScore >= 90D) {
            return "excellent";
        }
        if (qualityScore >= 80D) {
            return "good";
        }
        if (qualityScore >= 65D) {
            return "fair";
        }
        return "needs_improvement";
    }

    private double computeTopShare(Map<String, Integer> counter, int total) {
        if (counter == null || counter.isEmpty() || total <= 0) {
            return 0D;
        }
        int maxCount = counter.values().stream().max(Integer::compareTo).orElse(0);
        return (double) maxCount / (double) total;
    }

    private double normalizedEntropy(Map<String, Integer> counter) {
        if (counter == null || counter.isEmpty()) {
            return 0D;
        }
        double total = counter.values().stream().mapToDouble(Integer::doubleValue).sum();
        if (total <= 0D || counter.size() <= 1) {
            return counter.size() <= 1 ? 0D : 1D;
        }
        double entropy = 0D;
        for (Integer count : counter.values()) {
            if (count == null || count <= 0) {
                continue;
            }
            double probability = count / total;
            entropy -= probability * (Math.log(probability) / Math.log(2D));
        }
        double maxEntropy = Math.log(counter.size()) / Math.log(2D);
        if (maxEntropy <= 0D) {
            return 0D;
        }
        return clampScore(entropy / maxEntropy, 0D, 1D);
    }

    private List<String> buildQualityRiskFlags(double categoryTopShare,
                                               double merchantTopShare,
                                               double reasonCoverage,
                                               int categoryCount,
                                               int merchantCount,
                                               double priceSpanRate) {
        List<String> riskFlags = new ArrayList<>();
        if (categoryTopShare >= 0.65D || categoryCount <= 1) {
            riskFlags.add("类目集中度过高，推荐容易同质化");
        }
        if (merchantTopShare >= 0.7D || merchantCount <= 1) {
            riskFlags.add("商家集中度过高，存在供给单一风险");
        }
        if (reasonCoverage < 0.55D) {
            riskFlags.add("推荐理由覆盖不足，可解释性偏弱");
        }
        if (priceSpanRate < 0.25D) {
            riskFlags.add("价格带覆盖偏窄，难以承接不同预算");
        }
        return riskFlags;
    }

    private List<String> buildQualityOptimizationActions(List<String> riskFlags, int total) {
        List<String> actions = new ArrayList<>();
        if (riskFlags == null || riskFlags.isEmpty()) {
            actions.add("当前推荐质量稳定，建议持续做小流量探索和分群差异化 AB 实验。");
            return actions;
        }
        for (String riskFlag : riskFlags) {
            if (riskFlag.contains("类目")) {
                actions.add("将 20%-30% 推荐位分配给跨类目探索，控制同类商品连续曝光。");
            } else if (riskFlag.contains("商家")) {
                actions.add("加入商家去重约束，单商家曝光比例建议控制在 35% 以下。");
            } else if (riskFlag.contains("理由")) {
                actions.add("补充行为、实时热度、价格匹配等理由模板，提升解释完整度。");
            } else if (riskFlag.contains("价格")) {
                actions.add("按低/中/高预算分层召回，保证每档至少有 1-2 个候选。");
            }
        }
        if (total <= 3) {
            actions.add("当前候选商品过少，建议先扩充候选池再做精排。");
        }
        return actions.stream().distinct().collect(Collectors.toList());
    }

    private double clampScore(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private double roundDouble(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(Math.max(0, scale), RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void enrichIntentWithRealtimeProfile(Long userId, ShoppingIntentDTO intent) {
        if (userId == null || intent == null) {
            return;
        }

        try {
            Map<String, Object> dashboard = recommendationService.getRealtimeRecommendationDashboard(userId, GUIDE_RESULT_LIMIT);
            if (dashboard == null || dashboard.isEmpty()) {
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> segment = dashboard.get("segment") instanceof Map
                    ? (Map<String, Object>) dashboard.get("segment")
                    : Collections.emptyMap();

            intent.setSegmentCode(stringValue(segment.get("segmentCode")));
            intent.setSegmentName(stringValue(segment.get("segmentName")));
            intent.setPersonaSummary(firstNonEmpty(
                    stringValue(segment.get("personaSummary")),
                    stringValue(segment.get("llmSummary")),
                    stringValue(segment.get("segmentDescription"))
            ));
            intent.setStrategyHint(firstNonEmpty(
                    stringValue(segment.get("operationSuggestion")),
                    stringValue(segment.get("message"))
            ));
            intent.setTopCategories(toStringList(segment.get("topCategories")));
            intent.setTopTags(toStringList(segment.get("topTags")));
        } catch (Exception e) {
            log.warn("[AI] failed to enrich realtime persona for userId={}: {}", userId, e.getMessage());
        }
    }

    private Map<String, Object> buildIntentPersonaPayload(ShoppingIntentDTO intent) {
        if (intent == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (trimToNull(intent.getSegmentCode()) != null) {
            payload.put("segmentCode", intent.getSegmentCode());
        }
        if (trimToNull(intent.getSegmentName()) != null) {
            payload.put("segmentName", intent.getSegmentName());
        }
        if (trimToNull(intent.getPersonaSummary()) != null) {
            payload.put("summary", intent.getPersonaSummary());
        }
        if (trimToNull(intent.getStrategyHint()) != null) {
            payload.put("strategyHint", intent.getStrategyHint());
        }
        if (!intent.getTopCategories().isEmpty()) {
            payload.put("topCategories", intent.getTopCategories());
        }
        if (!intent.getTopTags().isEmpty()) {
            payload.put("topTags", intent.getTopTags());
        }
        return payload;
    }

    private Map<String, Object> buildShoppingBrief(ShoppingIntentDTO intent, List<Product> products) {
        if (intent == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> brief = new LinkedHashMap<>();
        String intentSummary = trimToNull(buildIntentSummary(intent));
        if (intentSummary != null) {
            brief.put("summary", intentSummary);
        }
        if (trimToNull(intent.getCategoryName()) != null) {
            brief.put("category", intent.getCategoryName());
        }

        String budgetText = buildBudgetText(intent);
        if (budgetText != null) {
            brief.put("budget", budgetText);
        }
        if (!intent.getPreferredBrands().isEmpty()) {
            brief.put("brands", trimAndLimitList(intent.getPreferredBrands(), 3));
        }
        if (!intent.getScenes().isEmpty()) {
            brief.put("scenes", trimAndLimitList(intent.getScenes(), 3));
        }
        if (!intent.getKeywords().isEmpty()) {
            brief.put("keywords", trimAndLimitList(intent.getKeywords(), 4));
        }

        brief.put("messageType", firstNonEmpty(intent.getMessageType(), "shopping_chat"));
        brief.put("recommendationMode", intent.isRecommendationMode());
        brief.put("productCount", products == null ? 0 : products.size());
        return brief;
    }

    private String buildBudgetText(ShoppingIntentDTO intent) {
        if (intent == null) {
            return null;
        }
        if (intent.getBudgetMin() != null && intent.getBudgetMax() != null) {
            return formatPrice(intent.getBudgetMin()) + " - " + formatPrice(intent.getBudgetMax()) + " 元";
        }
        if (intent.getBudgetMax() != null) {
            return formatPrice(intent.getBudgetMax()) + " 元以内";
        }
        if (intent.getBudgetMin() != null) {
            return formatPrice(intent.getBudgetMin()) + " 元以上";
        }
        return null;
    }

    private String buildStrategyLabel(ShoppingIntentDTO intent, List<Product> products) {
        if (intent == null) {
            return null;
        }
        if (intent.isNeedClarification()) {
            return "先补充需求，再继续收窄";
        }
        if ("product_comparison".equals(intent.getMessageType())) {
            return "双商品对比决策";
        }
        if ("product_question".equals(intent.getMessageType())) {
            return "单商品深入解答";
        }
        if (intent.isPreferAlternatives()) {
            return "差异化备选重排";
        }
        if (intent.isPreferMajorBrand()) {
            return "品牌稳妥优先";
        }
        if (intent.isPreferHighSales()) {
            return "高销量安心优先";
        }
        if (intent.isPreferLongTermUse()) {
            return "长期使用优先";
        }
        if (!intent.getTopCategories().isEmpty() || !intent.getTopTags().isEmpty()) {
            return "画像偏好承接";
        }
        if (products != null && products.size() >= 3) {
            return "个性化多候选推荐";
        }
        if (intent.isRecommendationMode()) {
            return "意图驱动导购";
        }
        return "自然语言购物助手";
    }

    private Map<String, Object> buildPersonaCard(ShoppingIntentDTO intent) {
        Map<String, Object> payload = buildIntentPersonaPayload(intent);
        if (payload.isEmpty()) {
            return Collections.emptyMap();
        }

        payload.remove("segmentCode");
        if (!payload.containsKey("segmentName") && trimToNull(intent.getPersonaSummary()) != null) {
            payload.put("segmentName", "当前兴趣画像");
        }

        String summary = trimToNull(stringValue(payload.get("summary")));
        if (summary == null && trimToNull(intent.getStrategyHint()) != null) {
            payload.put("summary", intent.getStrategyHint());
        }
        return payload;
    }

    private List<Map<String, Object>> buildInsightCards(ShoppingIntentDTO intent, List<Product> products) {
        if (intent == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> cards = new ArrayList<>();
        String intentSummary = trimToNull(buildIntentSummary(intent));
        if (intentSummary != null) {
            cards.add(buildInsightCard(
                    "识别到的需求",
                    intentSummary,
                    intent.isNeedClarification() ? "信息仍可继续补充" : "已具备推荐条件",
                    intent.isNeedClarification() ? "warning" : "primary"));
        }

        cards.add(buildInsightCard(
                "导购策略",
                firstNonEmpty(buildStrategyLabel(intent, products), "自然语言购物助手"),
                intent.isRecommendationMode() ? "会优先返回可下单候选" : "会先解答，再判断是否推荐",
                "success"));

        if (products != null && !products.isEmpty()) {
            Product leadProduct = products.get(0);
            String leadText = firstNonEmpty(trimToNull(leadProduct.getName()), "已生成候选商品");
            String descriptor = leadProduct.getRecommendReason();
            if (trimToNull(descriptor) == null) {
                descriptor = "共返回 " + products.size() + " 个可行动候选";
            }
            cards.add(buildInsightCard(
                    "首推候选",
                    leadText,
                    descriptor,
                    "accent"));
        } else if (trimToNull(intent.getClarificationQuestion()) != null) {
            cards.add(buildInsightCard(
                    "下一步建议",
                    "补充预算 / 品牌 / 场景",
                    intent.getClarificationQuestion(),
                    "warning"));
        }

        return cards.stream().limit(3).collect(Collectors.toList());
    }

    private Map<String, Object> buildInsightCard(String title, String value, String description, String tone) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", title);
        card.put("value", value);
        card.put("description", description);
        card.put("tone", tone);
        return card;
    }

    private List<Map<String, Object>> buildNextActions(ShoppingIntentDTO intent, List<Product> products) {
        if (intent == null) {
            return Collections.emptyList();
        }

        LinkedHashMap<String, Map<String, Object>> actions = new LinkedHashMap<>();
        if (products != null && !products.isEmpty()) {
            Product leadProduct = products.get(0);
            appendAction(actions, buildAction(
                    "view-product-" + leadProduct.getId(),
                    "查看首推商品",
                    firstNonEmpty(trimToNull(leadProduct.getName()), "打开商品详情"),
                    null,
                    "view_product",
                    leadProduct.getId()));
        }

        if (products != null && products.size() >= 2) {
            Product first = products.get(0);
            Product second = products.get(1);
            appendAction(actions, buildAction(
                    "compare-products",
                    "对比前两款",
                    "让我比较 " + buildPromptProductName(first.getName()) + " 和 " + buildPromptProductName(second.getName()),
                    "让我比较 " + buildPromptProductName(first.getName()) + " 和 " + buildPromptProductName(second.getName()),
                    "prompt",
                    null));
        }

        if (intent.isNeedClarification() && trimToNull(intent.getClarificationQuestion()) != null) {
            appendAction(actions, buildAction(
                    "clarify",
                    "继续缩小范围",
                    intent.getClarificationQuestion(),
                    intent.getClarificationQuestion(),
                    "prompt",
                    null));
        }

        for (String prompt : buildUserSuggestedPrompts(intent, products)) {
            appendAction(actions, buildAction(
                    "prompt-" + normalizeText(prompt),
                    "继续追问",
                    prompt,
                    prompt,
                    "prompt",
                    null));
            if (actions.size() >= 4) {
                break;
            }
        }

        return new ArrayList<>(actions.values());
    }

    private String normalizeText(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return "";
        }
        return prompt.trim().toLowerCase(Locale.ROOT);
    }

    private void appendAction(LinkedHashMap<String, Map<String, Object>> actions, Map<String, Object> action) {
        if (actions == null || action == null || action.isEmpty()) {
            return;
        }
        String key = trimToNull(stringValue(action.get("key")));
        if (key == null || actions.containsKey(key)) {
            return;
        }
        actions.put(key, action);
    }

    private Map<String, Object> buildAction(String key,
                                            String label,
                                            String description,
                                            String prompt,
                                            String type,
                                            Long productId) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("key", key);
        action.put("label", label);
        action.put("description", description);
        if (trimToNull(prompt) != null) {
            action.put("prompt", prompt);
        }
        action.put("type", firstNonEmpty(type, "prompt"));
        if (productId != null) {
            action.put("productId", productId);
        }
        return action;
    }

    private List<String> buildUserSuggestedPrompts(ShoppingIntentDTO intent, List<Product> products) {
        if (intent == null) {
            return buildStarterPrompts(null);
        }

        List<Product> safeProducts = products == null ? Collections.emptyList() : products.stream()
                .filter(Objects::nonNull)
                .filter(product -> product.getId() != null)
                .collect(Collectors.toList());
        LinkedHashMap<String, String> prompts = new LinkedHashMap<>();

        List<String> basePrompts = intent.isNeedClarification()
                ? buildClarificationPrompts(intent, safeProducts)
                : buildRefinementPrompts(intent, safeProducts);
        basePrompts.forEach(prompt -> appendPrompt(prompts, prompt));

        Product leadProduct = safeProducts.isEmpty() ? null : safeProducts.get(0);
        Product secondaryProduct = safeProducts.size() > 1 ? safeProducts.get(1) : null;
        if (leadProduct != null) {
            appendPrompt(prompts, buildPromptProductName(leadProduct.getName()) + "值不值得买");
        }
        if (leadProduct != null && secondaryProduct != null) {
            appendPrompt(prompts, "帮我比较" + buildPromptProductName(leadProduct.getName())
                    + "和" + buildPromptProductName(secondaryProduct.getName()));
        }
        if (!intent.isPreferAlternatives() && safeProducts.size() >= GUIDE_RESULT_LIMIT) {
            appendPrompt(prompts, "换一批风格差异更大的备选");
        }
        if (!intent.isPreferHighSales()) {
            appendPrompt(prompts, resolveCategoryPromptLabel(intent) + "，把销量更高的排前面");
        }
        if (!intent.isPreferMajorBrand() && intent.getPreferredBrands().isEmpty()) {
            appendPrompt(prompts, resolveCategoryPromptLabel(intent) + "，优先看品牌更稳的");
        }
        if (!intent.isPreferLongTermUse()) {
            appendPrompt(prompts, buildLongTermPrompt(intent));
        }
        if (!hasScene(intent, "通勤")) {
            appendPrompt(prompts, buildCommutePrompt(intent));
        }
        if (!hasScene(intent, "送人")) {
            appendPrompt(prompts, buildGiftPrompt(intent));
        }
        if (prompts.isEmpty()) {
            buildStarterPrompts(intent).forEach(prompt -> appendPrompt(prompts, prompt));
        }

        return prompts.values().stream().limit(4).collect(Collectors.toList());
    }

    private void appendPrompt(Map<String, String> prompts, String prompt) {
        String normalizedPrompt = trimToNull(prompt);
        if (prompts == null || normalizedPrompt == null) {
            return;
        }
        prompts.putIfAbsent(semanticPromptKey(normalizedPrompt), normalizedPrompt);
    }

    private String semanticPromptKey(String prompt) {
        String normalized = normalizeIntentText(prompt);
        if (normalized == null) {
            return UUID.randomUUID().toString();
        }
        if (containsSemanticIntent(normalized, ALTERNATIVE_HINTS)) {
            return "alternatives";
        }
        if (containsSemanticIntent(normalized, HIGH_SALES_HINTS) || normalized.contains("销量")) {
            return "sales";
        }
        if (containsSemanticIntent(normalized, MAJOR_BRAND_HINTS) || normalized.contains("品牌")) {
            return "brand";
        }
        if (containsSemanticIntent(normalized, LONG_TERM_USE_HINTS) || normalized.contains("长期")) {
            return "long-term";
        }
        if (normalized.contains("通勤")) {
            return "scene-commute";
        }
        if (normalized.contains("送礼") || normalized.contains("送人")) {
            return "scene-gift";
        }
        if (normalized.contains("比较") || normalized.contains("对比")) {
            return "compare";
        }
        if (normalized.contains("预算")) {
            return "budget:" + normalized.replaceAll("\\d+", "#");
        }
        if (normalized.contains("值不值得")) {
            return "product";
        }
        return normalized;
    }

    private boolean containsSemanticIntent(String normalizedPrompt, Collection<String> hints) {
        if (normalizedPrompt == null || hints == null) {
            return false;
        }
        for (String hint : hints) {
            String normalizedHint = normalizeIntentText(hint);
            if (normalizedHint != null && normalizedPrompt.contains(normalizedHint)) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildClarificationPrompts(ShoppingIntentDTO intent, List<Product> products) {
        LinkedHashSet<String> prompts = new LinkedHashSet<>();
        if (trimToNull(intent.getCategoryName()) == null) {
            prompts.addAll(buildStarterPrompts(intent));
            return new ArrayList<>(prompts);
        }

        prompts.addAll(buildBudgetConstraintPrompts(intent, products));
        if (intent.getPreferredBrands().isEmpty() && !intent.isPreferMajorBrand()) {
            prompts.addAll(buildBrandConstraintPrompts(intent, products));
        }
        if (!hasScene(intent, "通勤")) {
            prompts.add(buildCommutePrompt(intent));
        }
        if (!hasScene(intent, "送人")) {
            prompts.add(buildGiftPrompt(intent));
        }
        if (!intent.isPreferHighSales()) {
            prompts.add(resolveCategoryPromptLabel(intent) + "，销量更高优先");
        }
        if (intent.isPreferLongTermUse()) {
            prompts.add(buildLongTermPrompt(intent));
        }
        return new ArrayList<>(prompts);
    }

    private List<String> buildRefinementPrompts(ShoppingIntentDTO intent, List<Product> products) {
        LinkedHashSet<String> prompts = new LinkedHashSet<>();
        prompts.add(intent.isPreferAlternatives()
                ? "再换一批更有差异的备选"
                : "换一批不同侧重的备选");
        if (!intent.isPreferHighSales()) {
            prompts.add(resolveCategoryPromptLabel(intent) + "，销量更高优先");
        }
        if (!intent.isPreferMajorBrand() && intent.getPreferredBrands().isEmpty()) {
            prompts.addAll(buildBrandConstraintPrompts(intent, products));
        }
        if (!intent.isPreferLongTermUse()) {
            prompts.add(buildLongTermPrompt(intent));
        }
        if (!hasScene(intent, "通勤")) {
            prompts.add(buildCommutePrompt(intent));
        }
        if (!hasScene(intent, "送人")) {
            prompts.add(buildGiftPrompt(intent));
        }
        if (intent.getBudgetMin() == null && intent.getBudgetMax() == null) {
            prompts.addAll(buildBudgetConstraintPrompts(intent, products));
        }
        return new ArrayList<>(prompts);
    }

    private List<String> buildBudgetConstraintPrompts(ShoppingIntentDTO intent, List<Product> products) {
        if (intent == null || intent.getBudgetMin() != null || intent.getBudgetMax() != null) {
            return Collections.emptyList();
        }
        String categoryName = normalizeIntentText(intent.getCategoryName());
        String label = resolveCategoryPromptLabel(intent);
        if (categoryName != null && containsAny(categoryName, Arrays.asList("手机", "电脑", "笔记本", "平板"))) {
            return Arrays.asList(label + "，预算 2000 元以内", label + "，预算 3000 元左右");
        }
        if (categoryName != null && containsAny(categoryName, Arrays.asList("耳机", "护肤", "美妆", "键盘", "鼠标"))) {
            return Arrays.asList(label + "，预算 300 元以内", label + "，预算 500 元以内");
        }

        int suggestedBudget = suggestBudgetCap(products, resolveCategoryPromptLabel(intent));
        if (suggestedBudget <= 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(
                label + "，预算 " + suggestedBudget + " 元以内",
                label + "，预算 " + Math.max(200, (int) (suggestedBudget * 1.5d)) + " 元左右"
        );
    }

    private List<String> buildBrandConstraintPrompts(ShoppingIntentDTO intent, List<Product> products) {
        LinkedHashSet<String> prompts = new LinkedHashSet<>();
        for (String brand : extractLeadBrands(products)) {
            prompts.add(resolveCategoryPromptLabel(intent) + "，优先" + brand);
            if (prompts.size() >= 2) {
                return new ArrayList<>(prompts);
            }
        }

        String categoryName = normalizeIntentText(intent.getCategoryName());
        if (categoryName != null && containsAny(categoryName, Arrays.asList("手机", "耳机", "数码", "电脑"))) {
            prompts.add(resolveCategoryPromptLabel(intent) + "，优先苹果生态");
            prompts.add(resolveCategoryPromptLabel(intent) + "，优先华为");
        } else if (categoryName != null && containsAny(categoryName, Arrays.asList("护肤", "美妆"))) {
            prompts.add(resolveCategoryPromptLabel(intent) + "，优先兰蔻");
            prompts.add(resolveCategoryPromptLabel(intent) + "，优先欧莱雅");
        } else {
            prompts.add(resolveCategoryPromptLabel(intent) + "，优先看大品牌");
            prompts.add(resolveCategoryPromptLabel(intent) + "，优先看口碑更稳的");
        }
        return new ArrayList<>(prompts);
    }

    private List<String> extractLeadBrands(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> brands = new LinkedHashSet<>();
        for (Product product : products) {
            String haystack = buildProductHaystack(product, product == null ? null : product.getCategoryName());
            for (String brand : MAJOR_BRAND_KEYWORDS) {
                if (containsNormalizedTerm(haystack, brand)) {
                    brands.add(brand);
                    break;
                }
            }
            if (brands.size() >= 2) {
                break;
            }
        }
        return new ArrayList<>(brands);
    }

    private List<String> buildStarterPrompts(ShoppingIntentDTO intent) {
        LinkedHashSet<String> prompts = new LinkedHashSet<>();
        for (String category : resolvePersonaStarterCategories(intent)) {
            prompts.addAll(buildStarterPromptsForCategory(category));
            if (prompts.size() >= 4) {
                break;
            }
        }
        if (prompts.size() < 4) {
            prompts.addAll(Arrays.asList(
                    "预算 300 元左右的防晒",
                    "适合送礼的食品礼盒",
                    "通勤和办公都适合的小家电",
                    "学生党适合入手的平板"
            ));
        }
        return prompts.stream().limit(4).collect(Collectors.toList());
    }

    private String buildCommutePrompt(ShoppingIntentDTO intent) {
        String categoryName = normalizeIntentText(intent == null ? null : intent.getCategoryName());
        if (categoryName != null && containsAny(categoryName, Arrays.asList("手机", "耳机", "电脑", "数码"))) {
            return "更适合通勤的是哪款";
        }
        if (categoryName != null && containsAny(categoryName, Arrays.asList("护肤", "美妆", "防晒"))) {
            return "更适合日常通勤的是哪款";
        }
        return resolveCategoryPromptLabel(intent) + "，主要通勤使用";
    }

    private String buildGiftPrompt(ShoppingIntentDTO intent) {
        String categoryName = normalizeIntentText(intent == null ? null : intent.getCategoryName());
        if (categoryName != null && containsAny(categoryName, Arrays.asList("护肤", "美妆", "食品"))) {
            return "更适合送礼的是哪款";
        }
        return resolveCategoryPromptLabel(intent) + "，更适合送礼";
    }

    private String buildLongTermPrompt(ShoppingIntentDTO intent) {
        String categoryLabel = resolveCategoryPromptLabel(intent);
        if ("这类商品".equals(categoryLabel)) {
            return "更适合长期用的是哪款";
        }
        return categoryLabel + "，更适合长期使用";
    }

    private int suggestBudgetCap(List<Product> products, String categoryName) {
        if (products != null) {
            for (Product product : products) {
                if (product != null && product.getPrice() != null) {
                    return roundBudgetCap(product.getPrice());
                }
            }
        }
        String normalizedCategory = normalizeIntentText(categoryName);
        if (normalizedCategory == null) {
            return 500;
        }
        if (containsAny(normalizedCategory, Arrays.asList("手机", "电脑", "笔记本", "平板"))) {
            return 3000;
        }
        if (containsAny(normalizedCategory, Arrays.asList("耳机", "键盘", "鼠标", "护肤", "美妆"))) {
            return 500;
        }
        if (containsAny(normalizedCategory, Arrays.asList("食品", "零食", "礼盒", "生鲜", "茶", "咖啡"))) {
            return 300;
        }
        if (containsAny(normalizedCategory, Arrays.asList("家居", "家电", "厨具", "收纳", "保温", "办公"))) {
            return 800;
        }
        if (containsAny(normalizedCategory, Arrays.asList("服饰", "穿搭", "鞋", "外套", "箱包"))) {
            return 600;
        }
        return 1000;
    }

    private int roundBudgetCap(BigDecimal price) {
        if (price == null) {
            return 0;
        }
        double value = price.doubleValue();
        if (value <= 200) {
            return (int) (Math.ceil(value / 50.0d) * 50);
        }
        if (value <= 1000) {
            return (int) (Math.ceil(value / 100.0d) * 100);
        }
        if (value <= 5000) {
            return (int) (Math.ceil(value / 500.0d) * 500);
        }
        return (int) (Math.ceil(value / 1000.0d) * 1000);
    }

    private String resolveCategoryPromptLabel(ShoppingIntentDTO intent) {
        if (intent == null) {
            return "这类商品";
        }
        if (trimToNull(intent.getCategoryName()) != null) {
            return intent.getCategoryName();
        }
        String personaCategory = firstNonEmpty(
                firstNonBlank(intent.getTopCategories()),
                inferCategoryFromTopTags(intent.getTopTags()));
        return personaCategory == null ? "这类商品" : personaCategory;
    }

    private String buildPromptProductName(String productName) {
        String normalizedName = trimToNull(productName);
        if (normalizedName == null) {
            return "这款商品";
        }
        String compactName = normalizedName.replaceAll("\\s+", "");
        if (compactName.length() <= 16) {
            return compactName;
        }
        return compactName.substring(0, 16);
    }

    private boolean hasScene(ShoppingIntentDTO intent, String scene) {
        if (intent == null || trimToNull(scene) == null || intent.getScenes() == null) {
            return false;
        }
        for (String value : intent.getScenes()) {
            if (trimToNull(value) != null && value.contains(scene)) {
                return true;
            }
        }
        return false;
    }

    private ShoppingIntentDTO parseShoppingIntent(String userMessage, List<Map<String, String>> history) {
        ShoppingIntentDTO intent = new ShoppingIntentDTO();
        intent.setRawMessage(userMessage);
        intent.setAnalysisSource("rule");

        String contextMessage = buildIntentContext(history, userMessage);
        intent.setContextMessage(contextMessage);

        String normalizedMessage = normalizeIntentText(contextMessage);
        if (normalizedMessage == null) {
            return intent;
        }

        Map<Long, String> categoryNames = loadCategoryNameMap();
        ShoppingIntentDTO aiIntent = analyzeShoppingIntentWithAi(userMessage, contextMessage, history, categoryNames);
        applyRuleBasedIntent(intent, contextMessage, normalizedMessage, categoryNames);
        mergeAiIntent(intent, aiIntent, categoryNames);
        if (aiIntent != null) {
            intent.setAnalysisSource("llm_hybrid");
        }
        finalizeShoppingIntent(intent, normalizedMessage, categoryNames);

        return intent;
    }

    private void applyRuleBasedIntent(ShoppingIntentDTO intent,
                                      String contextMessage,
                                      String normalizedMessage,
                                      Map<Long, String> categoryNames) {
        Long categoryId = matchCategoryId(normalizedMessage, categoryNames);
        if (categoryId != null) {
            intent.setCategoryId(categoryId);
            intent.setCategoryName(categoryNames.get(categoryId));
        }

        BudgetRange budgetRange = extractBudgetRange(contextMessage);
        if (budgetRange != null) {
            intent.setBudgetMin(budgetRange.getMin());
            intent.setBudgetMax(budgetRange.getMax());
        }

        intent.setKeywords(extractIntentKeywords(normalizedMessage, intent.getCategoryName()));
        intent.setPreferredBrands(extractMatchedTerms(normalizedMessage, BRAND_KEYWORDS));
        intent.setScenes(extractMatchedTerms(normalizedMessage, SCENE_KEYWORDS));
        applyPreferenceHints(intent, normalizedMessage);

        boolean recommendationMode = looksLikeRecommendation(normalizedMessage, intent);
        intent.setRecommendationMode(recommendationMode);
        intent.setShoppingRelated(recommendationMode
                || hasEnoughShoppingSignals(intent)
                || intent.getBudgetMin() != null
                || intent.getBudgetMax() != null
                || containsAny(normalizedMessage, GENERAL_SHOPPING_HINTS));
        intent.setMessageType(intent.isShoppingRelated() ? "shopping_chat" : "general_chat");

        if (recommendationMode) {
            boolean needClarification = !hasEnoughShoppingSignals(intent) || shouldInviteRefinement(intent);
            intent.setNeedClarification(needClarification);
            intent.setMessageType("recommendation");
            if (needClarification) {
                intent.setClarificationQuestion(buildClarificationQuestion(intent));
            }
        }
    }

    private void applyPreferenceHints(ShoppingIntentDTO intent, String normalizedMessage) {
        if (intent == null || normalizedMessage == null) {
            return;
        }
        intent.setPreferHighSales(intent.isPreferHighSales() || containsAny(normalizedMessage, SALES_PRIORITY_HINTS));
        intent.setPreferMajorBrand(intent.isPreferMajorBrand() || containsAny(normalizedMessage, BIG_BRAND_PRIORITY_HINTS));
        intent.setPreferAlternatives(intent.isPreferAlternatives() || containsAny(normalizedMessage, ALTERNATIVE_PRIORITY_HINTS));
        intent.setPreferLongTermUse(intent.isPreferLongTermUse() || containsAny(normalizedMessage, LONG_TERM_PRIORITY_HINTS));
    }

    private String buildIntentContext(List<Map<String, String>> history, String userMessage) {
        LinkedList<String> parts = new LinkedList<>();
        if (history != null && !history.isEmpty()) {
            int capturedUserMessages = 0;
            for (int i = history.size() - 1; i >= 0 && capturedUserMessages < 2; i--) {
                Map<String, String> item = history.get(i);
                if (item == null) {
                    continue;
                }
                String role = trimToNull(item.get("role"));
                String content = trimToNull(item.get("content"));
                if (!"user".equals(role) || content == null) {
                    continue;
                }
                parts.addFirst(content);
                capturedUserMessages++;
            }
        }

        String currentMessage = trimToNull(userMessage);
        if (currentMessage != null) {
            parts.addLast(currentMessage);
        }

        return String.join(" ", parts);
    }

    private ShoppingIntentDTO analyzeShoppingIntentWithAi(String userMessage,
                                                          String contextMessage,
                                                          List<Map<String, String>> history,
                                                          Map<Long, String> categoryNames) {
        if (!isAiReady()) {
            return null;
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msgOf("system",
                "You are an intent parser for a Chinese ecommerce shopping assistant. "
                        + "Analyze the latest user message with recent conversation context. "
                        + "Return JSON only. Do not output markdown. "
                        + "messageType must be one of: greeting, thanks, farewell, general_chat, "
                        + "shopping_chat, recommendation, product_question, product_comparison. "
                        + "Use null for missing scalar fields and [] for missing arrays. "
                        + "If the user is asking for shopping advice but information is incomplete, "
                        + "set needClarification=true and provide a short clarificationQuestion in Chinese. "
                        + "JSON schema: "
                        + "{\"messageType\":\"...\",\"shoppingRelated\":true,\"recommendationMode\":false,"
                        + "\"needClarification\":false,\"clarificationQuestion\":null,"
                        + "\"preferHighSales\":false,\"preferMajorBrand\":false,"
                        + "\"preferAlternatives\":false,\"preferLongTermUse\":false,"
                        + "\"categoryName\":null,\"budgetMin\":null,\"budgetMax\":null,"
                        + "\"keywords\":[],\"preferredBrands\":[],\"scenes\":[]}"));
        messages.add(msgOf("user",
                "Available categories: " + loadPromptCategorySummary(categoryNames)
                        + "\nCurrent user message: " + firstNonEmpty(trimToNull(userMessage), "")
                        + "\nConversation context: " + firstNonEmpty(trimToNull(contextMessage), "")
                        + "\nRecent history: " + JSON.toJSONString(sanitizeHistory(history, userMessage))));

        try {
            String content = callChatCompletion(messages, INTENT_ANALYSIS_MAX_TOKENS, INTENT_ANALYSIS_TEMPERATURE);
            JSONObject json = parseJsonObjectResponse(content);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return mapAiIntent(json, categoryNames);
        } catch (Exception e) {
            log.warn("[AI] intent analysis fallback to rules: {}", e.getMessage());
            return null;
        }
    }

    private ShoppingIntentDTO mapAiIntent(JSONObject json, Map<Long, String> categoryNames) {
        ShoppingIntentDTO intent = new ShoppingIntentDTO();
        intent.setAnalysisSource("llm");
        String messageType = normalizeMessageType(trimToNull(json.getString("messageType")));
        intent.setMessageType(messageType);
        intent.setShoppingRelated(readBoolean(json.get("shoppingRelated"))
                || isShoppingMessageType(messageType));
        intent.setRecommendationMode(readBoolean(json.get("recommendationMode"))
                || "recommendation".equals(messageType));
        intent.setNeedClarification(readBoolean(json.get("needClarification")));
        intent.setClarificationQuestion(trimToNull(json.getString("clarificationQuestion")));
        intent.setPreferHighSales(readBoolean(json.get("preferHighSales")));
        intent.setPreferMajorBrand(readBoolean(json.get("preferMajorBrand")));
        intent.setPreferAlternatives(readBoolean(json.get("preferAlternatives")));
        intent.setPreferLongTermUse(readBoolean(json.get("preferLongTermUse")));

        String categoryName = trimToNull(json.getString("categoryName"));
        if (categoryName != null) {
            Long categoryId = matchCategoryId(normalizeIntentText(categoryName), categoryNames);
            if (categoryId != null) {
                intent.setCategoryId(categoryId);
                intent.setCategoryName(categoryNames.get(categoryId));
            } else {
                intent.setCategoryName(categoryName);
            }
        }

        BigDecimal budgetMin = safeBigDecimal(json.get("budgetMin"));
        BigDecimal budgetMax = safeBigDecimal(json.get("budgetMax"));
        if (budgetMin != null) {
            intent.setBudgetMin(budgetMin);
        }
        if (budgetMax != null) {
            intent.setBudgetMax(budgetMax);
        }

        intent.setKeywords(trimAndLimitList(toStringList(json.get("keywords")), INTENT_KEYWORD_LIMIT));
        intent.setPreferredBrands(trimAndLimitList(toStringList(json.get("preferredBrands")), INTENT_BRAND_LIMIT));
        intent.setScenes(trimAndLimitList(toStringList(json.get("scenes")), INTENT_SCENE_LIMIT));
        return intent;
    }

    private void mergeAiIntent(ShoppingIntentDTO target,
                               ShoppingIntentDTO aiIntent,
                               Map<Long, String> categoryNames) {
        if (target == null || aiIntent == null) {
            return;
        }

        if (aiIntent.getCategoryId() != null) {
            target.setCategoryId(aiIntent.getCategoryId());
            target.setCategoryName(aiIntent.getCategoryName());
        } else if (target.getCategoryId() == null && trimToNull(aiIntent.getCategoryName()) != null) {
            Long categoryId = matchCategoryId(normalizeIntentText(aiIntent.getCategoryName()), categoryNames);
            if (categoryId != null) {
                target.setCategoryId(categoryId);
                target.setCategoryName(categoryNames.get(categoryId));
            }
        }

        if (aiIntent.getBudgetMin() != null) {
            target.setBudgetMin(aiIntent.getBudgetMin());
        }
        if (aiIntent.getBudgetMax() != null) {
            target.setBudgetMax(aiIntent.getBudgetMax());
        }

        target.setKeywords(mergeIntentTerms(target.getKeywords(), aiIntent.getKeywords(), INTENT_KEYWORD_LIMIT));
        target.setPreferredBrands(mergeIntentTerms(target.getPreferredBrands(), aiIntent.getPreferredBrands(), INTENT_BRAND_LIMIT));
        target.setScenes(mergeIntentTerms(target.getScenes(), aiIntent.getScenes(), INTENT_SCENE_LIMIT));
        target.setPreferHighSales(target.isPreferHighSales() || aiIntent.isPreferHighSales());
        target.setPreferMajorBrand(target.isPreferMajorBrand() || aiIntent.isPreferMajorBrand());
        target.setPreferAlternatives(target.isPreferAlternatives() || aiIntent.isPreferAlternatives());
        target.setPreferLongTermUse(target.isPreferLongTermUse() || aiIntent.isPreferLongTermUse());

        target.setShoppingRelated(target.isShoppingRelated() || aiIntent.isShoppingRelated());
        target.setRecommendationMode(target.isRecommendationMode() || aiIntent.isRecommendationMode());

        String aiMessageType = normalizeMessageType(aiIntent.getMessageType());
        if (aiMessageType != null) {
            target.setMessageType(aiMessageType);
        }

        if (aiIntent.isNeedClarification()) {
            target.setNeedClarification(true);
            if (trimToNull(aiIntent.getClarificationQuestion()) != null) {
                target.setClarificationQuestion(aiIntent.getClarificationQuestion());
            }
        }
    }

    private void finalizeShoppingIntent(ShoppingIntentDTO intent,
                                        String normalizedMessage,
                                        Map<Long, String> categoryNames) {
        if (intent == null) {
            return;
        }

        if (intent.getCategoryId() == null && trimToNull(intent.getCategoryName()) != null) {
            Long categoryId = matchCategoryId(normalizeIntentText(intent.getCategoryName()), categoryNames);
            if (categoryId != null) {
                intent.setCategoryId(categoryId);
                intent.setCategoryName(categoryNames.get(categoryId));
            }
        }

        if (intent.getBudgetMin() != null && intent.getBudgetMax() != null
                && intent.getBudgetMin().compareTo(intent.getBudgetMax()) > 0) {
            BigDecimal min = intent.getBudgetMax();
            BigDecimal max = intent.getBudgetMin();
            intent.setBudgetMin(min);
            intent.setBudgetMax(max);
        }

        if (!intent.isShoppingRelated()) {
            intent.setShoppingRelated(hasEnoughShoppingSignals(intent)
                    || intent.getBudgetMin() != null
                    || intent.getBudgetMax() != null
                    || containsAny(normalizedMessage, GENERAL_SHOPPING_HINTS));
        }

        if (!intent.isRecommendationMode()) {
            intent.setRecommendationMode(looksLikeRecommendation(normalizedMessage, intent));
        }

        if (intent.isRecommendationMode()) {
            intent.setShoppingRelated(true);
        }

        String messageType = normalizeMessageType(intent.getMessageType());
        if (messageType == null) {
            intent.setMessageType(intent.isRecommendationMode()
                    ? "recommendation"
                    : (intent.isShoppingRelated() ? "shopping_chat" : "general_chat"));
        } else {
            intent.setMessageType(messageType);
        }

        if ("recommendation".equals(intent.getMessageType())) {
            intent.setRecommendationMode(true);
            if (!intent.isNeedClarification() && (!hasEnoughShoppingSignals(intent) || shouldInviteRefinement(intent))) {
                intent.setNeedClarification(true);
            }
        }

        if (intent.isNeedClarification() && trimToNull(intent.getClarificationQuestion()) == null) {
            intent.setClarificationQuestion(buildClarificationQuestion(intent));
        }
    }

    private List<String> mergeIntentTerms(List<String> left, List<String> right, int limit) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (left != null) {
            for (String value : left) {
                String normalized = trimToNull(value);
                if (normalized != null) {
                    merged.add(normalized);
                }
                if (merged.size() >= limit) {
                    return new ArrayList<>(merged);
                }
            }
        }
        if (right != null) {
            for (String value : right) {
                String normalized = trimToNull(value);
                if (normalized != null) {
                    merged.add(normalized);
                }
                if (merged.size() >= limit) {
                    return new ArrayList<>(merged);
                }
            }
        }
        return new ArrayList<>(merged);
    }

    private boolean isAiReady() {
        return StringUtils.hasText(apiUrl)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(model);
    }

    private boolean isShoppingMessageType(String messageType) {
        return "shopping_chat".equals(messageType)
                || "recommendation".equals(messageType)
                || "product_question".equals(messageType)
                || "product_comparison".equals(messageType);
    }

    private String normalizeMessageType(String rawMessageType) {
        String normalized = trimToNull(rawMessageType);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return VALID_INTENT_MESSAGE_TYPES.contains(normalized) ? normalized : null;
    }

    private boolean readBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String normalized = trimToNull(String.valueOf(value));
        if (normalized == null) {
            return false;
        }
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private void resolveMessageType(ShoppingIntentDTO intent, String userMessage, List<Product> mentionedProducts) {
        String existingMessageType = normalizeMessageType(intent.getMessageType());
        boolean conversationalOnly = "greeting".equals(existingMessageType)
                || "thanks".equals(existingMessageType)
                || "farewell".equals(existingMessageType);
        if (existingMessageType != null
                && (!conversationalOnly || !intent.isShoppingRelated())
                && !"shopping_chat".equals(existingMessageType)
                && !"general_chat".equals(existingMessageType)) {
            intent.setMessageType(existingMessageType);
            if ("recommendation".equals(existingMessageType)) {
                intent.setRecommendationMode(true);
                intent.setShoppingRelated(true);
            } else if (isShoppingMessageType(existingMessageType)) {
                intent.setShoppingRelated(true);
            }
            return;
        }

        String normalizedMessage = normalizeIntentText(intent.getContextMessage() != null ? intent.getContextMessage() : userMessage);
        if (normalizedMessage == null) {
            intent.setMessageType("general_chat");
            return;
        }

        if (containsAny(normalizedMessage, THANKS_HINTS)) {
            intent.setMessageType("thanks");
            return;
        }
        if (containsAny(normalizedMessage, FAREWELL_HINTS)) {
            intent.setMessageType("farewell");
            return;
        }
        if (isComparisonMessage(normalizedMessage, mentionedProducts)) {
            intent.setMessageType("product_comparison");
            intent.setShoppingRelated(true);
            intent.setRecommendationMode(false);
            return;
        }
        if (isProductQuestionMessage(normalizedMessage, mentionedProducts)) {
            intent.setMessageType("product_question");
            intent.setShoppingRelated(true);
            intent.setRecommendationMode(false);
            return;
        }
        if (intent.isRecommendationMode()) {
            intent.setMessageType("recommendation");
            return;
        }
        if (!intent.isShoppingRelated() && containsAny(normalizedMessage, GREETING_HINTS)) {
            intent.setMessageType("greeting");
            return;
        }
        if (intent.isShoppingRelated()) {
            intent.setMessageType("shopping_chat");
            return;
        }
        if (containsAny(normalizedMessage, GREETING_HINTS)) {
            intent.setMessageType("greeting");
            return;
        }
        intent.setMessageType("general_chat");
    }

    private String resolveConversationalReply(String userMessage, ShoppingIntentDTO intent) {
        String messageType = intent.getMessageType();
        if ("greeting".equals(messageType)) {
            return "你好，我是小优，可以帮你推荐商品、比较差异，也能回答商品相关问题。你可以直接告诉我想买什么，或者说出预算、品牌和使用场景。";
        }
        if ("thanks".equals(messageType)) {
            return "不客气，你继续告诉我想买什么或者还想比较哪几款，我接着帮你看。";
        }
        if ("farewell".equals(messageType)) {
            return "好的，有需要随时来找我。我可以继续帮你推荐、比较商品或者解答购物问题。";
        }
        if ("general_chat".equals(messageType) && !intent.isShoppingRelated()) {
            return "我是小优，这个商城的购物助手，主要帮你推荐商品、比较差异和解答购物问题。你可以直接告诉我想买什么。";
        }
        return null;
    }

    private boolean isComparisonMessage(String normalizedMessage, List<Product> mentionedProducts) {
        return mentionedProducts != null
                && mentionedProducts.size() >= 2
                && containsAny(normalizedMessage, COMPARISON_HINTS);
    }

    private String resolveSalesConversationalReply(String userMessage, ShoppingIntentDTO intent) {
        String fallback = resolveConversationalReply(userMessage, intent);
        String messageType = intent.getMessageType();
        if ("greeting".equals(messageType)) {
            return "你好，我来帮你挑商品。你可以直接告诉我预算、品牌偏好，或者说清楚是自己用、送礼、通勤还是学生党场景，我会按这个思路给你推荐。";
        }
        if ("thanks".equals(messageType)) {
            return "不客气，你继续告诉我想买什么，或者把纠结的几款发给我，我可以直接帮你缩到 1 到 2 个更值得看的选择。";
        }
        if ("farewell".equals(messageType)) {
            return "好的，你随时来找我。我可以继续帮你推荐、做对比，或者直接告诉你哪一款更适合下单。";
        }
        if ("general_chat".equals(messageType) && !intent.isShoppingRelated()) {
            return "我主要帮你做购物决策。你可以直接说想买什么、预算多少、给谁用、看重什么，我会按导购的方式给你更省时间的建议。";
        }
        return fallback;
    }

    private Map<String, Object> buildSalesComparisonResponse(ShoppingIntentDTO intent,
                                                             List<Product> mentionedProducts,
                                                             Map<Long, String> categoryNames) {
        if (mentionedProducts == null || mentionedProducts.size() < 2) {
            return buildAssistantResult("我还没完全识别出你想对比的两款商品。你可以把商品名说完整一点，比如“AirPods Pro 2 和华为 FreeBuds Pro 3 怎么选”。",
                    Collections.emptyList(), intent);
        }

        Product left = mentionedProducts.get(0);
        Product right = mentionedProducts.get(1);
        String leftCategory = categoryNames.getOrDefault(left.getCategoryId(), "其他");
        String rightCategory = categoryNames.getOrDefault(right.getCategoryId(), "其他");
        String reply = buildSalesComparisonReply(intent, left, right, leftCategory, rightCategory);
        return buildAssistantResult(reply, Arrays.asList(left, right), intent);
    }

    private Map<String, Object> buildSalesProductQuestionResponse(ShoppingIntentDTO intent,
                                                                  List<Product> mentionedProducts,
                                                                  Map<Long, String> categoryNames) {
        if (mentionedProducts == null || mentionedProducts.isEmpty()) {
            return buildAssistantResult("我还没准确识别出你在问哪一款商品。你可以把商品名说完整一点，比如“AirPods Pro 2 怎么样”或者“华为 FreeBuds Pro 3 值不值得买”。",
                    Collections.emptyList(), intent);
        }

        Product product = mentionedProducts.get(0);
        String categoryName = categoryNames.getOrDefault(product.getCategoryId(), "其他");
        return buildAssistantResult(buildSalesProductQuestionReply(intent, product, categoryName),
                Collections.singletonList(product), intent);
    }

    private boolean isProductQuestionMessage(String normalizedMessage, List<Product> mentionedProducts) {
        return mentionedProducts != null
                && !mentionedProducts.isEmpty()
                && (containsAny(normalizedMessage, PRODUCT_QUESTION_HINTS)
                || normalizedMessage.contains("?")
                || normalizedMessage.contains("？"));
    }

    private List<Product> findMentionedProducts(List<Product> products,
                                                Map<Long, String> categoryNames,
                                                ShoppingIntentDTO intent) {
        String normalizedMessage = normalizeIntentText(intent.getContextMessage());
        if (normalizedMessage == null || normalizedMessage.isEmpty() || products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<Product, Integer>> scored = new ArrayList<>();
        for (Product product : products) {
            if (product == null) {
                continue;
            }
            int score = scoreMentionedProduct(product, categoryNames.get(product.getCategoryId()), normalizedMessage, intent);
            if (score >= 30) {
                scored.add(new AbstractMap.SimpleEntry<>(product, score));
            }
        }

        scored.sort((left, right) -> Integer.compare(right.getValue(), left.getValue()));
        return scored.stream()
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int scoreMentionedProduct(Product product,
                                      String categoryName,
                                      String normalizedMessage,
                                      ShoppingIntentDTO intent) {
        int score = 0;
        for (String alias : buildProductAliases(product)) {
            if (normalizedMessage.toLowerCase(Locale.ROOT).contains(alias.toLowerCase(Locale.ROOT))) {
                score += alias.length() >= 6 ? 100 : 60;
                break;
            }
        }

        int brandHits = 0;
        for (String brand : intent.getPreferredBrands()) {
            if (containsNormalizedTerm(buildProductHaystack(product, categoryName), brand)) {
                brandHits++;
                score += 18;
            }
        }

        int keywordHits = 0;
        for (String keyword : intent.getKeywords()) {
            if (containsNormalizedTerm(buildProductHaystack(product, categoryName), keyword)) {
                keywordHits++;
                score += 10;
            }
        }

        if ((brandHits > 0 || keywordHits > 1 || score >= 100)
                && intent.getCategoryId() != null
                && intent.getCategoryId().equals(product.getCategoryId())) {
            score += 6;
        }

        return score;
    }

    private List<String> buildProductAliases(Product product) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        String normalizedName = normalizeIntentText(product.getName());
        if (normalizedName != null) {
            aliases.add(normalizedName);
        }

        String rawName = trimToNull(product.getName());
        if (rawName != null) {
            String[] nameTokens = rawName.split("[\\s\\-_/]+");
            if (nameTokens.length >= 2) {
                String withoutFirstToken = normalizeIntentText(String.join("", Arrays.copyOfRange(nameTokens, 1, nameTokens.length)));
                if (withoutFirstToken != null) {
                    aliases.add(withoutFirstToken);
                }
            }
            for (String token : nameTokens) {
                String normalizedToken = normalizeIntentText(token);
                if (normalizedToken != null && normalizedToken.length() >= 4) {
                    aliases.add(normalizedToken);
                }
            }
        }

        return new ArrayList<>(aliases);
    }

    private boolean containsNormalizedTerm(String haystack, String term) {
        String normalizedHaystack = normalizeIntentText(haystack);
        String normalizedTerm = normalizeIntentText(term);
        return normalizedHaystack != null
                && normalizedTerm != null
                && normalizedHaystack.toLowerCase(Locale.ROOT).contains(normalizedTerm.toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> buildProductComparisonResponse(ShoppingIntentDTO intent,
                                                               List<Product> mentionedProducts,
                                                               Map<Long, String> categoryNames) {
        if (mentionedProducts == null || mentionedProducts.size() < 2) {
            String reply = "我还没完全识别出你想对比的两款商品。你可以直接告诉我更完整的商品名，比如“AirPods Pro 2 和华为 FreeBuds Pro 3 怎么选”。";
            return buildAssistantResult(reply, Collections.emptyList(), intent);
        }

        Product left = mentionedProducts.get(0);
        Product right = mentionedProducts.get(1);
        String leftCategory = categoryNames.getOrDefault(left.getCategoryId(), "其他");
        String rightCategory = categoryNames.getOrDefault(right.getCategoryId(), "其他");

        StringBuilder reply = new StringBuilder();
        reply.append("我先帮你对比一下这两款：\n");
        reply.append("1. ").append(buildComparisonLine(left, leftCategory)).append("\n");
        reply.append("2. ").append(buildComparisonLine(right, rightCategory)).append("\n");
        reply.append(buildComparisonRecommendation(intent, left, right, leftCategory, rightCategory));

        return buildAssistantResult(reply.toString(), Arrays.asList(left, right), intent);
    }

    private String buildComparisonLine(Product product, String categoryName) {
        List<String> highlights = extractProductHighlights(product, categoryName);
        List<String> parts = new ArrayList<>();
        if (product.getPrice() != null) {
            parts.add("价格 " + formatPrice(product.getPrice()) + " 元");
        }
        if (product.getRating() != null) {
            parts.add(String.format(Locale.ROOT, "评分 %.1f", product.getRating()));
        }
        if (product.getSalesCount() != null) {
            parts.add("销量 " + product.getSalesCount());
        }
        if (!highlights.isEmpty()) {
            parts.add("亮点 " + String.join("、", highlights));
        } else {
            parts.add("分类 " + categoryName);
        }
        return product.getName() + "：" + String.join("，", parts);
    }

    private String buildComparisonRecommendation(ShoppingIntentDTO intent,
                                                 Product left,
                                                 Product right,
                                                 String leftCategory,
                                                 String rightCategory) {
        String normalizedMessage = normalizeIntentText(intent.getContextMessage());
        Product recommended = chooseBetterProductForQuestion(normalizedMessage, intent, left, right, leftCategory, rightCategory);
        Product alternative = recommended.getId().equals(left.getId()) ? right : left;

        StringBuilder reply = new StringBuilder();
        if ((intent.getBudgetMin() != null || intent.getBudgetMax() != null) && isWithinBudget(recommended, intent)) {
            reply.append("如果你更看重预算匹配和当前需求，我更偏向推荐 [")
                    .append(recommended.getName())
                    .append("](product:")
                    .append(recommended.getId())
                    .append(")。");
        } else if (normalizedMessage != null && normalizedMessage.contains("性价比")) {
            reply.append("如果你更看重性价比，我会更偏向价格更友好的 [")
                    .append(recommended.getName())
                    .append("](product:")
                    .append(recommended.getId())
                    .append(")。");
        } else {
            reply.append("综合价格、口碑和你的提问重点来看，我更偏向 [")
                    .append(recommended.getName())
                    .append("](product:")
                    .append(recommended.getId())
                    .append(")。");
        }

        reply.append(" 如果你愿意，我还可以继续按预算、品牌或使用场景，帮你判断它和 [")
                .append(alternative.getName())
                .append("](product:")
                .append(alternative.getId())
                .append(") 哪个更适合你。");
        return reply.toString();
    }

    private Product chooseBetterProductForQuestion(String normalizedMessage,
                                                   ShoppingIntentDTO intent,
                                                   Product left,
                                                   Product right,
                                                   String leftCategory,
                                                   String rightCategory) {
        if ((intent.getBudgetMin() != null || intent.getBudgetMax() != null)) {
            boolean leftWithin = isWithinBudget(left, intent);
            boolean rightWithin = isWithinBudget(right, intent);
            if (leftWithin && !rightWithin) {
                return left;
            }
            if (rightWithin && !leftWithin) {
                return right;
            }
        }

        if (normalizedMessage != null && normalizedMessage.contains("性价比")
                && left.getPrice() != null && right.getPrice() != null) {
            return left.getPrice().compareTo(right.getPrice()) <= 0 ? left : right;
        }

        double leftScore = scoreQuestionFit(left, leftCategory, normalizedMessage, intent);
        double rightScore = scoreQuestionFit(right, rightCategory, normalizedMessage, intent);
        return leftScore >= rightScore ? left : right;
    }

    private double scoreQuestionFit(Product product,
                                    String categoryName,
                                    String normalizedMessage,
                                    ShoppingIntentDTO intent) {
        double score = 0;
        String haystack = normalizeIntentText(buildProductHaystack(product, categoryName));
        if (product.getRating() != null) {
            score += product.getRating().doubleValue() * 4.0;
        }
        if (product.getSalesCount() != null) {
            score += Math.min(product.getSalesCount() / 600.0, 10.0);
        }
        if ((intent.getBudgetMin() != null || intent.getBudgetMax() != null) && isWithinBudget(product, intent)) {
            score += 12.0;
        }
        if (normalizedMessage != null) {
            score += collectMatchedTerms(haystack, intent.getKeywords()).size() * 12.0;
            score += collectMatchedTerms(haystack, intent.getPreferredBrands()).size() * 10.0;
            score += collectMatchedTerms(haystack, intent.getScenes()).size() * 8.0;
        }
        if (intent.isPreferHighSales() && product.getSalesCount() != null) {
            score += Math.min(product.getSalesCount() / 220.0, 14.0);
        }
        if (intent.isPreferMajorBrand()) {
            score += calculateBrandPreferenceBoost(haystack);
        }
        if (intent.isPreferLongTermUse()) {
            score += calculateLongTermUseBoost(product, haystack);
        }
        return score;
    }

    private Map<String, Object> buildProductQuestionResponse(ShoppingIntentDTO intent,
                                                             List<Product> mentionedProducts,
                                                             Map<Long, String> categoryNames) {
        if (mentionedProducts == null || mentionedProducts.isEmpty()) {
            String reply = "我还没准确识别出你问的是哪款商品。你可以直接告诉我完整商品名，比如“AirPods Pro 2 怎么样”或“华为 FreeBuds Pro 3 支持降噪吗”。";
            return buildAssistantResult(reply, Collections.emptyList(), intent);
        }

        Product product = mentionedProducts.get(0);
        String categoryName = categoryNames.getOrDefault(product.getCategoryId(), "其他");
        String reply = buildProductQuestionReply(intent, product, categoryName);
        return buildAssistantResult(reply, Collections.singletonList(product), intent);
    }

    private String buildProductQuestionReply(ShoppingIntentDTO intent, Product product, String categoryName) {
        String normalizedMessage = normalizeIntentText(intent.getContextMessage());
        if (normalizedMessage == null) {
            return buildProductOverview(product, categoryName);
        }

        if (containsAny(normalizedMessage, Arrays.asList("价格", "多少钱", "贵吗"))) {
            return product.getName() + " 目前价格是 "
                    + formatPrice(product.getPrice()) + " 元。"
                    + appendRatingAndSales(product);
        }
        if (containsAny(normalizedMessage, Arrays.asList("评分", "口碑", "销量"))) {
            return product.getName() + appendRatingAndSales(product)
                    + " 如果你想和同类商品比较，我也可以继续帮你横向看。";
        }
        if (containsAny(normalizedMessage, Arrays.asList("降噪", "续航", "音质", "蓝牙", "无线", "头戴", "入耳"))) {
            return buildFeatureAnswer(product, categoryName, normalizedMessage);
        }
        if (normalizedMessage.contains("适合") && !intent.getScenes().isEmpty()) {
            return buildSuitabilityAnswer(product, categoryName, intent.getScenes().get(0));
        }
        return buildProductOverview(product, categoryName);
    }

    private String buildFeatureAnswer(Product product, String categoryName, String normalizedMessage) {
        String haystack = buildProductHaystack(product, categoryName);
        List<String> askedFeatures = collectMatchedTerms(normalizedMessage, Arrays.asList(
                "降噪", "续航", "音质", "蓝牙", "无线", "头戴", "入耳", "快充", "轻薄"
        ));
        if (askedFeatures.isEmpty()) {
            return buildProductOverview(product, categoryName);
        }

        List<String> supported = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String feature : askedFeatures) {
            if (containsNormalizedTerm(haystack, feature)) {
                supported.add(feature);
            } else {
                unknown.add(feature);
            }
        }

        StringBuilder reply = new StringBuilder();
        reply.append("关于 ").append(product.getName()).append("：");
        if (!supported.isEmpty()) {
            reply.append("从当前商品信息看，它有提到 ")
                    .append(String.join("、", supported))
                    .append("。");
        }
        if (!unknown.isEmpty()) {
            reply.append(" 当前商品信息里没有明确写到 ")
                    .append(String.join("、", unknown))
                    .append("，如果你很看重这点，我建议你再看一下详情页参数或告诉我，我可以帮你找更匹配的替代款。");
        }
        if (supported.isEmpty() && !unknown.isEmpty()) {
            reply.append(appendRatingAndSales(product));
        }
        return reply.toString();
    }

    private String buildSuitabilityAnswer(Product product, String categoryName, String scene) {
        String haystack = buildProductHaystack(product, categoryName);
        List<String> reasons = new ArrayList<>();
        if (containsNormalizedTerm(haystack, "降噪") && ("通勤".equals(scene) || "办公".equals(scene))) {
            reasons.add("带降噪特性，通勤和办公会更友好");
        }
        if (containsNormalizedTerm(haystack, "续航")) {
            reasons.add("商品信息里强调了续航");
        }
        if (containsNormalizedTerm(haystack, "低延迟") && "游戏".equals(scene)) {
            reasons.add("提到了低延迟，更适合游戏场景");
        }
        if (containsNormalizedTerm(haystack, "便携") || containsNormalizedTerm(haystack, "轻")) {
            reasons.add("更偏便携");
        }

        if (reasons.isEmpty()) {
            return "从目前商品信息看，" + product.getName() + " 属于 " + categoryName + "，价格 "
                    + formatPrice(product.getPrice()) + " 元。"
                    + " 但它是否特别适合" + scene + "，当前描述里没有特别明确的信号。"
                    + " 如果你告诉我更看重什么，我可以再帮你换个更贴合的方向。";
        }

        return "从当前商品信息看，" + product.getName() + " 比较适合" + scene + "，因为" + String.join("、", reasons)
                + "。如果你愿意，我也可以顺手帮你找同场景下更值得买的替代款。";
    }

    private String buildProductOverview(Product product, String categoryName) {
        List<String> highlights = extractProductHighlights(product, categoryName);
        StringBuilder reply = new StringBuilder();
        reply.append("关于 ").append(product.getName()).append("，我目前查到的信息是：");
        if (product.getPrice() != null) {
            reply.append("价格 ").append(formatPrice(product.getPrice())).append(" 元；");
        }
        if (product.getRating() != null) {
            reply.append(String.format(Locale.ROOT, "评分 %.1f；", product.getRating()));
        }
        if (product.getSalesCount() != null) {
            reply.append("销量 ").append(product.getSalesCount()).append("；");
        }
        if (!highlights.isEmpty()) {
            reply.append("亮点有 ").append(String.join("、", highlights)).append("。");
        } else if (trimToNull(product.getDescription()) != null) {
            reply.append(product.getDescription());
        } else {
            reply.append("分类是 ").append(categoryName).append("。");
        }
        reply.append(" 如果你想知道它和别的商品怎么选，我也可以继续帮你比较。");
        return reply.toString();
    }

    private String appendRatingAndSales(Product product) {
        List<String> parts = new ArrayList<>();
        if (product.getRating() != null) {
            parts.add(String.format(Locale.ROOT, "评分 %.1f", product.getRating()));
        }
        if (product.getSalesCount() != null) {
            parts.add("销量 " + product.getSalesCount());
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "，" + String.join("，", parts) + "。";
    }

    private List<String> extractProductHighlights(Product product, String categoryName) {
        LinkedHashSet<String> highlights = new LinkedHashSet<>();
        if (product.getTags() != null) {
            for (String tag : product.getTags()) {
                String normalizedTag = normalizeIntentText(tag);
                if (normalizedTag == null || normalizedTag.length() <= 1) {
                    continue;
                }
                if (categoryName != null && containsNormalizedTerm(categoryName, tag)) {
                    continue;
                }
                highlights.add(tag);
                if (highlights.size() >= 2) {
                    break;
                }
            }
        }
        if (highlights.isEmpty() && trimToNull(product.getDescription()) != null) {
            for (String keyword : FEATURE_KEYWORDS) {
                if (containsNormalizedTerm(product.getDescription(), keyword)) {
                    highlights.add(keyword);
                    if (highlights.size() >= 2) {
                        break;
                    }
                }
            }
        }
        return new ArrayList<>(highlights);
    }

    private Map<String, Object> buildIntentAwareFallbackResponse(ShoppingIntentDTO intent,
                                                                 String userMessage,
                                                                 List<Product> mentionedProducts,
                                                                 Map<Long, String> categoryNames) {
        if (intent.isRecommendationMode()) {
            return buildShoppingFallbackResponse(userMessage, intent);
        }
        if ("product_comparison".equals(intent.getMessageType())) {
            return buildSalesComparisonResponse(intent, mentionedProducts, categoryNames);
        }
        if ("product_question".equals(intent.getMessageType())) {
            return buildSalesProductQuestionResponse(intent, mentionedProducts, categoryNames);
        }
        if (intent.isShoppingRelated()) {
            return buildShoppingFallbackResponse(userMessage, intent);
        }
        return buildAssistantResult(
                "我是小优，这个商城的购物助手，可以帮你推荐商品、比较差异和解答购物问题。你可以直接告诉我想买什么。",
                Collections.emptyList(),
                intent
        );
    }

    private boolean looksLikeRecommendation(String normalizedMessage, ShoppingIntentDTO intent) {
        if (normalizedMessage == null) {
            return false;
        }

        if (containsAny(normalizedMessage, Arrays.asList("区别", "差别", "原理", "是什么", "为什么"))
                && !containsAny(normalizedMessage, RECOMMENDATION_HINTS)
                && intent.getBudgetMin() == null
                && intent.getBudgetMax() == null) {
            return false;
        }

        boolean hasCategoryOrKeyword = hasEnoughShoppingSignals(intent);
        boolean hasBudget = intent.getBudgetMin() != null || intent.getBudgetMax() != null;
        boolean hasHint = containsAny(normalizedMessage, RECOMMENDATION_HINTS);
        if (hasHint && (hasCategoryOrKeyword || hasBudget || !intent.getScenes().isEmpty())) {
            return true;
        }

        if (hasCategoryOrKeyword && hasBudget) {
            return true;
        }

        return hasCategoryOrKeyword && normalizedMessage.length() <= 20;
    }

    private boolean hasEnoughShoppingSignals(ShoppingIntentDTO intent) {
        return intent.getCategoryId() != null
                || !intent.getKeywords().isEmpty()
                || !intent.getPreferredBrands().isEmpty();
    }

    private boolean shouldInviteRefinement(ShoppingIntentDTO intent) {
        return intent.getCategoryId() != null
                && intent.getBudgetMin() == null
                && intent.getBudgetMax() == null
                && intent.getPreferredBrands().isEmpty()
                && intent.getScenes().isEmpty()
                && intent.getKeywords().size() <= 1;
    }

    private String buildClarificationQuestion(ShoppingIntentDTO intent) {
        if (intent.getCategoryId() == null && intent.getKeywords().isEmpty() && intent.getPreferredBrands().isEmpty()) {
            return "你想买哪一类商品？可以直接告诉我品类、预算、品牌或使用场景，比如“500元以内的蓝牙耳机”。";
        }
        if (intent.getBudgetMin() == null && intent.getBudgetMax() == null) {
            return "如果你有预算范围，也可以继续告诉我，比如“500元以内”或“1000到1500元”，我可以再帮你缩小范围。";
        }
        if (intent.getPreferredBrands().isEmpty() && intent.getScenes().isEmpty()) {
            return "如果你有品牌偏好或使用场景，也可以继续告诉我，我能再帮你筛得更准。";
        }
        return "如果你还想继续缩小范围，可以告诉我预算、品牌或使用场景。";
    }

    private Long matchCategoryId(String normalizedMessage, Map<Long, String> categoryNames) {
        Long bestCategoryId = null;
        int bestScore = 0;
        Map<?, ?> rawCategoryNames = categoryNames == null ? Collections.emptyMap() : categoryNames;
        String lowerMessage = normalizedMessage.toLowerCase(Locale.ROOT);
        for (Map.Entry<?, ?> entry : rawCategoryNames.entrySet()) {
            Long categoryId = parseLong(entry.getKey());
            String categoryName = entry.getValue() == null ? null : trimToNull(entry.getValue().toString());
            if (categoryId == null || categoryName == null) {
                continue;
            }

            int score = 0;
            for (String alias : categoryAliases(categoryName)) {
                String normalizedAlias = normalizeIntentText(alias);
                if (normalizedAlias != null && lowerMessage.contains(normalizedAlias.toLowerCase(Locale.ROOT))) {
                    score += Math.max(1, normalizedAlias.length());
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestCategoryId = categoryId;
            }
        }
        return bestCategoryId;
    }

    private List<String> extractIntentKeywords(String normalizedMessage, String matchedCategoryName) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        keywords.addAll(extractMatchedTerms(normalizedMessage, FEATURE_KEYWORDS));
        if (matchedCategoryName != null) {
            keywords.addAll(extractMatchedTerms(normalizedMessage, categoryAliases(matchedCategoryName)));
        }
        return new ArrayList<>(keywords);
    }

    private List<String> extractMatchedTerms(String normalizedMessage, Collection<String> dictionary) {
        if (normalizedMessage == null || normalizedMessage.isEmpty() || dictionary == null || dictionary.isEmpty()) {
            return Collections.emptyList();
        }

        String lowerMessage = normalizedMessage.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (String rawTerm : dictionary) {
            String normalizedTerm = normalizeIntentText(rawTerm);
            if (normalizedTerm == null) {
                continue;
            }
            if (lowerMessage.contains(normalizedTerm.toLowerCase(Locale.ROOT))) {
                matches.add(rawTerm);
            }
        }
        return new ArrayList<>(matches);
    }

    private boolean containsAny(String text, Collection<String> terms) {
        if (text == null || text.isEmpty() || terms == null || terms.isEmpty()) {
            return false;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        for (String rawTerm : terms) {
            String normalizedTerm = normalizeIntentText(rawTerm);
            if (normalizedTerm != null && lowerText.contains(normalizedTerm.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> categoryAliases(String categoryName) {
        List<String> aliases = new ArrayList<>();
        if (categoryName == null) {
            return aliases;
        }

        aliases.add(categoryName);
        switch (categoryName) {
            case "手机数码":
                aliases.addAll(Arrays.asList("手机", "数码", "耳机", "蓝牙耳机", "无线耳机", "降噪耳机", "平板", "手表", "音箱"));
                break;
            case "电脑办公":
                aliases.addAll(Arrays.asList("电脑", "笔记本", "办公", "显示器", "键盘", "鼠标", "打印机"));
                break;
            case "家用电器":
                aliases.addAll(Arrays.asList("家电", "电器", "空调", "冰箱", "洗衣机", "烤箱", "电饭煲", "吸尘器"));
                break;
            case "服饰鞋包":
                aliases.addAll(Arrays.asList("衣服", "外套", "鞋", "运动鞋", "鞋包", "背包", "穿搭"));
                break;
            case "美妆护肤":
                aliases.addAll(Arrays.asList("护肤", "美妆", "面膜", "精华", "防晒", "洗面奶", "口红", "彩妆"));
                break;
            case "食品生鲜":
                aliases.addAll(Arrays.asList("食品", "生鲜", "零食", "牛奶", "咖啡", "水果", "饮料"));
                break;
            case "图书文具":
                aliases.addAll(Arrays.asList("图书", "书", "文具", "笔", "练习册", "阅读"));
                break;
            case "运动户外":
                aliases.addAll(Arrays.asList("运动", "户外", "跑步", "健身", "露营", "瑜伽"));
                break;
            case "母婴玩具":
                aliases.addAll(Arrays.asList("母婴", "宝宝", "婴儿", "奶粉", "纸尿裤", "玩具", "积木"));
                break;
            case "家居家装":
                aliases.addAll(Arrays.asList("家居", "家装", "台灯", "收纳", "保温杯", "窗帘", "地毯"));
                break;
            default:
                break;
        }
        return aliases;
    }

    private Map<String, Object> buildGuidedShoppingResponse(Long userId, ShoppingIntentDTO intent) {
        if (!hasEnoughShoppingSignals(intent)) {
            intent.setNeedClarification(true);
            intent.setClarificationQuestion(buildClarificationQuestion(intent));
            return buildAssistantResult(intent.getClarificationQuestion(), Collections.emptyList(), intent);
        }

        Map<Long, String> categoryNames = loadCategoryNameMap();
        List<Product> activeProducts = constrainProductsToIntentCategory(loadActiveProductsForGuide(), intent, false);
        Set<Long> personalizedProductIds = loadPersonalizedProductIds(userId);
        List<GuideCandidate> rankedCandidates = rankGuideProducts(activeProducts, categoryNames, intent, personalizedProductIds);
        List<Product> selectedProducts = selectGuideProducts(rankedCandidates, intent);
        if (selectedProducts.isEmpty()) {
            return buildShoppingFallbackResponse(intent.getRawMessage(), intent);
        }
        attachGuideReasons(selectedProducts, rankedCandidates, categoryNames, intent);
        String reply = buildSalesGuidedShoppingReply(intent, rankedCandidates, selectedProducts, categoryNames);
        return buildAssistantResult(reply, selectedProducts, intent);
    }

    private void attachGuideReasons(List<Product> selectedProducts,
                                    List<GuideCandidate> rankedCandidates,
                                    Map<Long, String> categoryNames,
                                    ShoppingIntentDTO intent) {
        if (selectedProducts == null || selectedProducts.isEmpty()) {
            return;
        }

        Map<Long, GuideCandidate> candidateMap = rankedCandidates.stream()
                .filter(candidate -> candidate != null && candidate.getProduct() != null && candidate.getProduct().getId() != null)
                .collect(Collectors.toMap(
                        candidate -> candidate.getProduct().getId(),
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        for (Product product : selectedProducts) {
            if (product == null || product.getId() == null) {
                continue;
            }
            GuideCandidate candidate = candidateMap.get(product.getId());
            String categoryName = categoryNames.getOrDefault(product.getCategoryId(), "其他");
            if (candidate != null) {
                product.setRecommendReason(buildOptimizedGuidedReason(candidate, categoryName, intent));
                product.setRecommendationSourceType(candidate.isPersonalized() ? "AI_PERSONALIZED" : "AI_GUIDED");
            } else {
                product.setRecommendReason("匹配你当前表达的选购条件");
                product.setRecommendationSourceType("AI_GUIDED");
            }
            if (!StringUtils.hasText(product.getRecommendationSegmentName()) && StringUtils.hasText(intent.getSegmentName())) {
                product.setRecommendationSegmentName(intent.getSegmentName());
            }
            if (!StringUtils.hasText(product.getRecommendationSegmentCode()) && StringUtils.hasText(intent.getSegmentCode())) {
                product.setRecommendationSegmentCode(intent.getSegmentCode());
            }
        }
    }

    private List<Product> loadActiveProductsForGuide() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getSalesCount));
    }

    private Set<Long> loadPersonalizedProductIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }

        try {
            return recommendationService.getPersonalRecommendations(userId, PERSONALIZED_HINT_LIMIT).stream()
                    .map(Product::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception e) {
            log.warn("[AI] load personalized recommendations failed: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private List<GuideCandidate> rankGuideProducts(List<Product> products,
                                                   Map<Long, String> categoryNames,
                                                   ShoppingIntentDTO intent,
                                                   Set<Long> personalizedProductIds) {
        List<GuideCandidate> candidates = new ArrayList<>();
        for (Product product : products) {
            if (product == null) {
                continue;
            }
            if (product.getStock() != null && product.getStock() <= 0) {
                continue;
            }

            GuideCandidate candidate = buildGuideCandidate(
                    product,
                    categoryNames.get(product.getCategoryId()),
                    intent,
                    personalizedProductIds
            );
            if (candidate.isRelevant()) {
                candidates.add(candidate);
            }
        }

        candidates.sort(Comparator.comparingDouble(GuideCandidate::getScore).reversed());
        return candidates;
    }

    private GuideCandidate buildGuideCandidate(Product product,
                                               String categoryName,
                                               ShoppingIntentDTO intent,
                                               Set<Long> personalizedProductIds) {
        String haystack = normalizeIntentText(buildProductHaystack(product, categoryName));
        List<String> matchedTerms = new ArrayList<>();
        matchedTerms.addAll(collectMatchedTerms(haystack, intent.getKeywords()));
        matchedTerms.addAll(collectMatchedTerms(haystack, intent.getPreferredBrands()));
        matchedTerms.addAll(collectMatchedTerms(haystack, intent.getScenes()));
        matchedTerms = new ArrayList<>(new LinkedHashSet<>(matchedTerms));

        boolean categoryMatched = intent.getCategoryId() != null && intent.getCategoryId().equals(product.getCategoryId());
        boolean withinBudget = isWithinBudget(product, intent);
        boolean personalized = personalizedProductIds.contains(product.getId());
        boolean strictCategoryFiltering = intent.getCategoryId() != null;
        boolean explicitMatched = categoryMatched || !matchedTerms.isEmpty();
        boolean relevant = strictCategoryFiltering ? categoryMatched : explicitMatched;

        double score = 0;
        if (categoryMatched) {
            score += 40.0;
        }
        score += matchedTerms.size() * 18.0;
        if (withinBudget) {
            score += 20.0;
        }
        if (personalized) {
            score += explicitMatched ? 12.0 : 2.0;
        }

        if (product.getPrice() != null) {
            if (intent.getBudgetMax() != null && product.getPrice().compareTo(intent.getBudgetMax()) > 0) {
                BigDecimal delta = product.getPrice().subtract(intent.getBudgetMax());
                score -= Math.min(delta.doubleValue() / 50.0, 20.0);
            }
            if (intent.getBudgetMin() != null && product.getPrice().compareTo(intent.getBudgetMin()) < 0) {
                BigDecimal delta = intent.getBudgetMin().subtract(product.getPrice());
                score -= Math.min(delta.doubleValue() / 60.0, 8.0);
            }
        }

        if (product.getRating() != null) {
            score += product.getRating().doubleValue() * 3.5;
        }
        if (product.getSalesCount() != null) {
            score += Math.min(product.getSalesCount() / 450.0, 12.0);
        }
        if (intent.isPreferHighSales() && product.getSalesCount() != null) {
            score += Math.min(product.getSalesCount() / 180.0, 18.0);
        }
        if (intent.isPreferMajorBrand()) {
            score += calculateBrandPreferenceBoost(haystack);
        }
        if (intent.isPreferLongTermUse()) {
            score += calculateLongTermUseBoost(product, haystack);
        }
        if (intent.getCategoryId() != null && !categoryMatched) {
            score -= 16.0;
        }

        return new GuideCandidate(product, score, relevant, withinBudget, categoryMatched, personalized, matchedTerms);
    }

    private double calculateBrandPreferenceBoost(String haystack) {
        if (haystack == null) {
            return 0;
        }
        int hits = collectMatchedTerms(haystack, MAINSTREAM_BRAND_HINTS).size();
        if (hits <= 0) {
            return 0;
        }
        return 8.0 + Math.min((hits - 1) * 3.0, 6.0);
    }

    private double calculateLongTermUseBoost(Product product, String haystack) {
        double score = 0;
        if (product.getRating() != null) {
            score += product.getRating().doubleValue() * 1.8;
        }
        if (product.getSalesCount() != null) {
            score += Math.min(product.getSalesCount() / 900.0, 6.0);
        }
        score += collectMatchedTerms(haystack, LONG_TERM_PRODUCT_HINTS).size() * 4.0;
        return score;
    }

    private List<String> collectMatchedTerms(String haystack, List<String> terms) {
        if (haystack == null || haystack.isEmpty() || terms == null || terms.isEmpty()) {
            return Collections.emptyList();
        }

        String lowerHaystack = haystack.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String rawTerm : terms) {
            String normalizedTerm = normalizeIntentText(rawTerm);
            if (normalizedTerm == null) {
                continue;
            }
            if (lowerHaystack.contains(normalizedTerm.toLowerCase(Locale.ROOT))) {
                matches.add(rawTerm);
            }
        }
        return matches;
    }

    private boolean isWithinBudget(Product product, ShoppingIntentDTO intent) {
        if (product.getPrice() == null) {
            return true;
        }
        if (intent.getBudgetMin() != null && product.getPrice().compareTo(intent.getBudgetMin()) < 0) {
            return false;
        }
        return intent.getBudgetMax() == null || product.getPrice().compareTo(intent.getBudgetMax()) <= 0;
    }

    private List<Product> selectGuideProducts(List<GuideCandidate> rankedCandidates, ShoppingIntentDTO intent) {
        if (rankedCandidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<GuideCandidate> withinBudget = rankedCandidates.stream()
                .filter(GuideCandidate::isWithinBudget)
                .collect(Collectors.toList());
        List<GuideCandidate> candidatePool = withinBudget.isEmpty() ? rankedCandidates : withinBudget;
        int skip = 0;
        if (intent != null && intent.isPreferAlternatives() && candidatePool.size() > GUIDE_RESULT_LIMIT) {
            skip = candidatePool.size() >= GUIDE_RESULT_LIMIT + 2 ? 2 : 1;
        }
        return candidatePool.stream()
                .skip(skip)
                .limit(GUIDE_RESULT_LIMIT)
                .map(GuideCandidate::getProduct)
                .collect(Collectors.toList());
    }

    private String buildGuidedShoppingReply(ShoppingIntentDTO intent,
                                            List<GuideCandidate> rankedCandidates,
                                            List<Product> selectedProducts,
                                            Map<Long, String> categoryNames) {
        if (selectedProducts.isEmpty()) {
            if (trimToNull(intent.getClarificationQuestion()) != null) {
                return intent.getClarificationQuestion();
            }
            return "我这边暂时没找到合适的商品，你可以告诉我预算、品类、品牌偏好或使用场景，我再帮你继续筛。";
        }

        Map<Long, GuideCandidate> candidateMap = rankedCandidates.stream()
                .collect(Collectors.toMap(
                        candidate -> candidate.getProduct().getId(),
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        boolean allOutOfBudget = (intent.getBudgetMin() != null || intent.getBudgetMax() != null)
                && selectedProducts.stream().noneMatch(product -> isWithinBudget(product, intent));

        StringBuilder reply = new StringBuilder();
        String intentSummary = buildIntentSummary(intent);
        if (allOutOfBudget && intent.getBudgetMax() != null) {
            reply.append("按你目前的预算，我这边没有完全卡在预算内的现货，不过先给你几款最接近需求的参考：\n");
        } else if (intentSummary != null) {
            reply.append("我先按").append(intentSummary).append("给你筛了这几款：\n");
        } else {
            reply.append("我先按你的需求给你筛了这几款：\n");
        }

        for (int i = 0; i < selectedProducts.size(); i++) {
            Product product = selectedProducts.get(i);
            GuideCandidate candidate = candidateMap.get(product.getId());
            String categoryName = categoryNames.getOrDefault(product.getCategoryId(), "其他");
            reply.append(i + 1)
                    .append(". [")
                    .append(product.getName())
                    .append("](product:")
                    .append(product.getId())
                    .append(")：")
                    .append(buildOptimizedGuidedReason(candidate, categoryName, intent))
                    .append("\n");
        }

        if (trimToNull(intent.getClarificationQuestion()) != null) {
            reply.append(intent.getClarificationQuestion());
        } else {
            reply.append("如果你愿意，我还可以继续按预算、品牌或使用场景帮你缩小范围。");
        }
        return reply.toString();
    }

    private String buildGuidedReason(GuideCandidate candidate, String categoryName, ShoppingIntentDTO intent) {
        List<String> reasons = new ArrayList<>();
        Product product = candidate.getProduct();
        if (product.getPrice() != null) {
            if ((intent.getBudgetMin() != null || intent.getBudgetMax() != null) && isWithinBudget(product, intent)) {
                reasons.add("价格 " + formatPrice(product.getPrice()) + " 元，在你的范围内");
            } else if (intent.getBudgetMax() != null) {
                reasons.add("价格 " + formatPrice(product.getPrice()) + " 元，略高于当前预算");
            } else {
                reasons.add("价格 " + formatPrice(product.getPrice()) + " 元");
            }
        }

        if (!candidate.getMatchedTerms().isEmpty()) {
            reasons.add("匹配 " + String.join("、", candidate.getMatchedTerms().subList(0, Math.min(2, candidate.getMatchedTerms().size()))));
        }
        if (candidate.isPersonalized()) {
            reasons.add("也贴近你最近的浏览偏好");
        }
        if (product.getRating() != null) {
            reasons.add(String.format(Locale.ROOT, "评分 %.1f", product.getRating()));
        }
        if (product.getSalesCount() != null) {
            reasons.add("销量 " + product.getSalesCount());
        }
        if (reasons.isEmpty()) {
            reasons.add("分类是 " + categoryName);
        }
        return String.join("，", reasons);
    }

    private String buildOptimizedGuidedReason(GuideCandidate candidate, String categoryName, ShoppingIntentDTO intent) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        Product product = candidate.getProduct();
        String haystack = buildProductHaystack(product, categoryName);

        if (product.getPrice() != null) {
            if ((intent.getBudgetMin() != null || intent.getBudgetMax() != null) && isWithinBudget(product, intent)) {
                reasons.add("价格在你当前预算范围内");
            } else if (intent.getBudgetMax() != null) {
                reasons.add("价格接近你当前的预算带");
            } else {
                reasons.add("当前到手价约 " + formatPrice(product.getPrice()) + " 元");
            }
        }

        if (!candidate.getMatchedTerms().isEmpty()) {
            reasons.add("卖点和你关注的“"
                    + String.join("、", candidate.getMatchedTerms().subList(0, Math.min(2, candidate.getMatchedTerms().size())))
                    + "”更贴合");
        }

        if (candidate.isCategoryMatched() && trimToNull(categoryName) != null) {
            reasons.add("属于你正在筛选的" + categoryName + "方向");
        }

        if (candidate.isPersonalized()) {
            reasons.add("和你最近关注的商品方向一致");
        }

        if (product.getRating() != null && product.getRating().compareTo(BigDecimal.valueOf(4.6)) >= 0) {
            reasons.add(String.format(Locale.ROOT, "口碑评分 %.1f，整体反馈更稳", product.getRating()));
        } else if (product.getSalesCount() != null && product.getSalesCount() > 0) {
            reasons.add(product.getSalesCount() >= 1000 ? "销量表现靠前" : "已有稳定成交表现");
        }
        if (intent != null && intent.isPreferHighSales() && product.getSalesCount() != null) {
            reasons.add(product.getSalesCount() >= 1000 ? "更符合你现在想优先看高销量的筛选方式" : "成交表现比普通候选更稳");
        }
        if (intent != null && intent.isPreferMajorBrand() && calculateBrandPreferenceBoost(haystack) > 0) {
            reasons.add("品牌认知度更高，更适合想选稳一点的方案");
        }
        if (intent != null && intent.isPreferLongTermUse() && calculateLongTermUseBoost(product, haystack) >= 8.0) {
            reasons.add("更偏向长期使用时更省心的类型");
        }

        if (reasons.isEmpty()) {
            reasons.add("综合匹配你当前这轮选购需求");
        }

        return reasons.stream().limit(3).collect(Collectors.joining("；"));
    }

    private String pickVariant(String seed, String... options) {
        if (options == null || options.length == 0) {
            return "";
        }
        int index = Math.floorMod(Objects.hashCode(seed), options.length);
        return options[index];
    }

    private String buildSalesGuidedShoppingReply(ShoppingIntentDTO intent,
                                                  List<GuideCandidate> rankedCandidates,
                                                  List<Product> selectedProducts,
                                                  Map<Long, String> categoryNames) {
        if (selectedProducts == null || selectedProducts.isEmpty()) {
            return buildSalesFallbackReply(intent == null ? null : intent.getRawMessage(), intent, Collections.emptyList(), categoryNames, false);
        }

        Map<Long, GuideCandidate> candidateMap = rankedCandidates.stream()
                .collect(Collectors.toMap(
                        candidate -> candidate.getProduct().getId(),
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        boolean allOutOfBudget = intent != null
                && (intent.getBudgetMin() != null || intent.getBudgetMax() != null)
                && selectedProducts.stream().noneMatch(product -> isWithinBudget(product, intent));

        Product leadProduct = selectedProducts.get(0);
        StringBuilder reply = new StringBuilder();
        reply.append(buildGuidedOpening(intent, allOutOfBudget))
                .append("\n")
                .append(buildGuidedLeadPrompt(intent, leadProduct))
                .append("\n");

        for (int i = 0; i < selectedProducts.size(); i++) {
            Product product = selectedProducts.get(i);
            GuideCandidate candidate = candidateMap.get(product.getId());
            String categoryName = categoryNames.getOrDefault(product.getCategoryId(), "其他");
            reply.append(i + 1)
                    .append(". [")
                    .append(product.getName())
                    .append("](product:")
                    .append(product.getId())
                    .append(")：")
                    .append(buildOptimizedGuidedReason(candidate, categoryName, intent))
                    .append("。\n");
        }

        reply.append(buildGuidedClosing(intent));
        return reply.toString();
    }

    private String buildSalesComparisonReply(ShoppingIntentDTO intent,
                                              Product left,
                                             Product right,
                                             String leftCategory,
                                             String rightCategory) {
        String normalizedMessage = intent == null ? null : normalizeIntentText(intent.getContextMessage());
        Product recommended = chooseBetterProductForQuestion(normalizedMessage, intent, left, right, leftCategory, rightCategory);
        Product alternative = Objects.equals(recommended.getId(), left.getId()) ? right : left;

        StringBuilder reply = new StringBuilder();
        reply.append(buildComparisonOpening(intent, normalizedMessage, recommended));

        reply.append("\n1. ").append(buildSalesComparisonLine(left, leftCategory)).append("\n");
        reply.append("2. ").append(buildSalesComparisonLine(right, rightCategory)).append("\n");
        reply.append(buildComparisonClosing(intent, alternative));
        return reply.toString();
    }

    private String buildSalesComparisonLine(Product product, String categoryName) {
        List<String> parts = new ArrayList<>();
        if (product.getPrice() != null) {
            parts.add("到手价约 " + formatPrice(product.getPrice()) + " 元");
        }
        List<String> highlights = extractProductHighlights(product, categoryName);
        if (!highlights.isEmpty()) {
            parts.add("主打 " + String.join("、", highlights));
        }
        if (product.getRating() != null) {
            parts.add(String.format(Locale.ROOT, "口碑 %.1f", product.getRating()));
        } else if (product.getSalesCount() != null && product.getSalesCount() > 0) {
            parts.add("销量 " + product.getSalesCount());
        }
        if (parts.isEmpty()) {
            parts.add("属于" + categoryName + "方向");
        }
        return product.getName() + "：" + String.join("，", parts);
    }

    private String buildSalesProductQuestionReply(ShoppingIntentDTO intent, Product product, String categoryName) {
        String normalizedMessage = intent == null ? null : normalizeIntentText(intent.getContextMessage());
        List<String> highlights = extractProductHighlights(product, categoryName);
        List<String> points = new ArrayList<>();

        if (product.getPrice() != null) {
            points.add("现在到手价大约 " + formatPrice(product.getPrice()) + " 元");
        }
        if (!highlights.isEmpty()) {
            points.add("核心卖点偏向 " + String.join("、", highlights));
        }
        if (product.getRating() != null) {
            points.add(String.format(Locale.ROOT, "口碑评分 %.1f", product.getRating()));
        } else if (product.getSalesCount() != null && product.getSalesCount() > 0) {
            points.add("已经有一定成交表现");
        }

        StringBuilder reply = new StringBuilder();
        reply.append(buildProductQuestionOpening(intent, normalizedMessage, product));
        if (!points.isEmpty()) {
            reply.append(String.join("，", points)).append("。");
        } else {
            reply.append("这款目前能看到的信息不算少，属于").append(categoryName).append("里比较稳妥的一类。");
        }
        reply.append(buildProductQuestionClosing(intent, normalizedMessage, product));
        return reply.toString();
    }

    private String buildSalesFallbackReply(String userMessage,
                                           ShoppingIntentDTO intent,
                                           List<Product> products,
                                           Map<Long, String> categoryNames,
                                           boolean exactMatchMissed) {
        BigDecimal budget = resolveBudgetReference(intent, userMessage);
        if (products == null || products.isEmpty()) {
            if (intent != null && intent.isPreferAlternatives()) {
                return "这轮我还没筛到更有差异的现货备选。你可以再告诉我预算上限、品牌范围，或者想要通勤 / 送礼 / 长期使用哪种方向，我继续给你换一组。";
            }
            return "我这轮还没筛到特别合适的现货。你可以把预算、品牌偏好，或者自己用 / 送礼 / 通勤 / 学生党这类场景再说具体一点，我就能继续往下缩。";
        }

        boolean hasBudgetMatch = budget == null || products.stream()
                .anyMatch(product -> product.getPrice() == null || product.getPrice().compareTo(budget) <= 0);

        StringBuilder reply = new StringBuilder();
        String fallbackSeed = firstNonEmpty(intent == null ? null : intent.getContextMessage(), userMessage, "fallback");
        if (exactMatchMissed) {
            reply.append(pickVariant(fallbackSeed,
                    "我先按你这次说的需求搜了一轮，暂时没找到完全匹配的现货。先给你几款更接近这个想法的相关商品。",
                    "按你刚刚的条件，我这边暂时没有完全命中的现货。我先把更接近你这个想法的几款相关商品列出来给你参考。",
                    "我先照着你这次的条件查了一轮，暂时没有完全匹配的商品。下面这几款会更接近你的需求方向。"));
        } else if (budget != null && !hasBudgetMatch) {
            reply.append(pickVariant(fallbackSeed,
                    "按你现在的预算，我先保留几款最接近需求、也更值得继续看的。",
                    "这轮我先按“尽量贴近你当前预算”的思路，留了几款更值得继续看的候选。",
                    "按你现在这档预算，我先把最接近需求的几款留下来，方便你继续比。"));
        } else if (intent != null && intent.isPreferAlternatives()) {
            reply.append(pickVariant(fallbackSeed,
                    "这次我先换一组更有差异的备选，方便你快速拉开对比。",
                    "这轮我先给你换一批路线差异更明显的候选，方便你更快看出区别。",
                    "我这次先不沿用原来的顺序，直接换一组更有区分度的备选给你。"));
        } else if (intent != null && intent.isPreferHighSales()) {
            reply.append(pickVariant(fallbackSeed,
                    "如果你更看重销量和市场验证，我先把成交表现更强的几款提到前面。",
                    "这轮我先按销量和成交表现排一遍，把更稳的几款放前面。",
                    "如果你想先看更有市场验证的一批，我先把销量更靠前的候选提到前面。"));
        } else if (intent != null && intent.isPreferMajorBrand()) {
            reply.append(pickVariant(fallbackSeed,
                    "如果你更想先看品牌更稳的大厂款，我先把这几款放到前面。",
                    "这轮我先从品牌更稳的方向筛，把大厂和主流品牌里的几款摆在前面。",
                    "如果你更在意品牌稳妥度，我先给你看品牌认知度更高的几款。"));
        } else if (intent != null && intent.isPreferLongTermUse()) {
            reply.append(pickVariant(fallbackSeed,
                    "如果你更看重长期使用体验，我先把更稳妥、更耐用的几款列出来。",
                    "这轮我先按长期使用的稳定感来筛，把更省心的几款列在前面。",
                    "如果你是想买一个能久用的，我先把更偏耐用和稳定的候选挑出来。"));
        } else {
            reply.append(pickVariant(fallbackSeed,
                    "我先帮你挑了几款更容易继续判断的，你可以先从第一款看起。",
                    "我先把这轮更有代表性的几款列出来，方便你直接往下比。",
                    "我先把更值得继续看的几款挑出来了，你可以先从第一款开始看。"));
        }
        reply.append("\n");

        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            String categoryName = categoryNames.getOrDefault(product.getCategoryId(), "其他");
            reply.append(i + 1)
                    .append(". [")
                    .append(product.getName())
                    .append("](product:")
                    .append(product.getId())
                    .append(")：")
                    .append(buildSalesFallbackReason(product, categoryName, budget))
                    .append("。\n");
        }

        reply.append(buildFallbackClosing(intent));
        return reply.toString();
    }

    private String buildGuidedOpening(ShoppingIntentDTO intent, boolean allOutOfBudget) {
        String intentSummary = intent == null ? null : buildIntentSummary(intent);
        String seed = firstNonEmpty(intentSummary, intent == null ? null : intent.getContextMessage(), "guided");
        if (allOutOfBudget) {
            return pickVariant(seed,
                    "按你现在的预算，我先保留最接近需求的几款给你，方便你先看清差距。",
                    "这轮我先按“尽量贴近预算和需求”的思路，给你留下几款更值得继续比的。",
                    "按你现在这档预算，我先挑出几款虽然不算完全卡线、但整体更接近需求的候选。");
        }
        if (intent != null && intent.isPreferAlternatives()) {
            return pickVariant(seed,
                    "这次我换一组更有差异的备选，方便你更快看出路线区别。",
                    "这轮我先给你换一批风格更拉开的候选，方便你快速对比方向差异。",
                    "我这次不沿着原来的顺序排，先给你看一组差异感更强的备选。");
        }
        if (intent != null && intent.isPreferHighSales()) {
            return pickVariant(seed,
                    "如果你更看重销量和市场验证，我先把成交表现更强的几款排在前面。",
                    "这轮我先按销量和成交表现筛一遍，把更稳的几款提到前面。",
                    "如果你想先看更有市场验证的一批，我先把销量更靠前的候选摆在前面。");
        }
        if (intent != null && intent.isPreferMajorBrand()) {
            return pickVariant(seed,
                    "如果你更想先看品牌更稳的大厂款，我先把这几款拎出来。",
                    "这轮我先从品牌认知度更高的方向筛，把更容易放心下单的几款放前面。",
                    "如果你更在意品牌稳妥度，我先给你看大厂和主流品牌里更值得优先看的几款。");
        }
        if (intent != null && intent.isPreferLongTermUse()) {
            return pickVariant(seed,
                    "如果你更看重长期使用体验，我先把更稳妥、更耐用的几款放在前面。",
                    "这轮我先按长期使用的稳定感来筛，把更省心的几款提到前面。",
                    "如果你是想买一个能久用的，我先把更偏耐用和稳定的候选挑出来。");
        }
        if (trimToNull(intentSummary) != null) {
            return pickVariant(seed,
                    "我先按" + intentSummary + "这个方向，筛出更值得优先看的几款。",
                    "这轮我先围绕" + intentSummary + "筛了一遍，把更容易下决定的几款摆在前面。",
                    "我先顺着" + intentSummary + "这条线，给你缩到几款更值得重点看的商品。");
        }
        return pickVariant(seed,
                "我先把当前更值得优先看的几款排出来，你可以直接从第一款开始看。",
                "我先把这轮更有代表性的几款筛出来了，方便你直接进入对比。",
                "我先帮你把更容易继续判断的几款列出来，你可以先看前两款。");
    }

    private String buildGuidedLeadPrompt(ShoppingIntentDTO intent, Product leadProduct) {
        String productRef = productRef(leadProduct);
        String seed = firstNonEmpty(leadProduct == null ? null : leadProduct.getName(),
                intent == null ? null : intent.getContextMessage(),
                "lead");
        if (intent != null && intent.isPreferHighSales()) {
            return pickVariant(seed,
                    "如果你想先看大众接受度更高的一款，可以先点开" + productRef + "。",
                    "如果你想先锁定一款销量更稳的，我建议先看" + productRef + "。",
                    "如果你想先看成交表现最稳的代表款，可以先点开" + productRef + "。");
        }
        if (intent != null && intent.isPreferMajorBrand()) {
            return pickVariant(seed,
                    "如果你想先看品牌更稳的一款，可以先点开" + productRef + "。",
                    "如果你想先从品牌认知度更高的一款开始看，我建议先点开" + productRef + "。",
                    "如果你想先看更容易放心下单的一款，可以先从" + productRef + "开始。");
        }
        if (intent != null && intent.isPreferLongTermUse()) {
            return pickVariant(seed,
                    "如果你想先看更适合长期持有的一款，可以先点开" + productRef + "。",
                    "如果你更在意长期使用的稳定感，先从" + productRef + "看会更顺。",
                    "如果你想先看更偏耐用的一款，我会先建议点开" + productRef + "。");
        }
        if (intent != null && intent.isPreferAlternatives()) {
            return pickVariant(seed,
                    "如果你想先看这轮里差异最明显的一款，可以先点开" + productRef + "。",
                    "如果你想先看这一组里路线最鲜明的一款，可以先从" + productRef + "开始。",
                    "如果你想先感受这轮备选的风格差异，先点开" + productRef + "会更直观。");
        }
        return pickVariant(seed,
                "如果你想先快速锁定一款，可以先点开" + productRef + "。",
                "如果你想先看一款最有代表性的，我会先建议你点开" + productRef + "。",
                "如果你想先抓一个重点款，我建议先从" + productRef + "看起。");
    }

    private String buildGuidedClosing(ShoppingIntentDTO intent) {
        String seed = firstNonEmpty(intent == null ? null : intent.getContextMessage(), "guided-closing");
        if (intent != null && trimToNull(intent.getClarificationQuestion()) != null) {
            return pickVariant(seed,
                    "如果你愿意，我可以继续收窄：" + intent.getClarificationQuestion(),
                    "如果你想继续往下缩，我可以顺着这个问题继续筛：" + intent.getClarificationQuestion());
        }
        if (intent != null && intent.isPreferAlternatives()) {
            return pickVariant(seed,
                    "如果你还想继续换一组，我可以再按更省预算、大品牌或通勤方向重排。",
                    "如果你还想再换一轮，我可以继续按预算、品牌或场景把路线重新拉开。",
                    "如果你想继续换方向，我还可以再按销量、品牌或通勤需求重排一轮。");
        }
        if (intent != null && intent.isPreferLongTermUse()) {
            return pickVariant(seed,
                    "如果你愿意，我还能继续按续航、做工稳定性和通勤体验，把范围再缩到 1 到 2 款。",
                    "如果你想更进一步，我可以继续从续航、稳定性和长期使用成本这几个角度替你收窄。",
                    "如果你愿意，我还可以继续按耐用度、续航和场景适配，把范围压到 1 到 2 款。");
        }
        return pickVariant(seed,
                "如果你告诉我更在意销量、品牌、长期使用还是通勤 / 送礼场景，我还能继续缩到 1 到 2 款。",
                "如果你补一句更偏向销量、品牌、送礼还是通勤使用，我还能继续帮你收窄。",
                "如果你愿意再补一个重点维度，比如品牌、销量或通勤场景，我可以继续筛到 1 到 2 款。");
    }

    private String buildComparisonOpening(ShoppingIntentDTO intent, String normalizedMessage, Product recommended) {
        String productRef = productRef(recommended);
        String seed = firstNonEmpty(recommended == null ? null : recommended.getName(), normalizedMessage, "compare");
        if (intent != null && intent.isPreferHighSales()) {
            return pickVariant(seed,
                    "如果你更看重销量和市场验证，我会更偏向" + productRef + "。它在大众接受度上会更稳。",
                    "按你现在偏向销量优先的思路，我会更倾向于" + productRef + "。它的成交表现更扎实。",
                    "如果先看市场验证这一维，我会更偏向" + productRef + "。它的销量和接受度会更稳。");
        }
        if (intent != null && intent.isPreferMajorBrand()) {
            return pickVariant(seed,
                    "如果你更想先押品牌，我会更偏向" + productRef + "。它的品牌认知和选择门槛会更低一些。",
                    "按你现在更在意品牌稳妥度的思路，我会更偏向" + productRef + "。它会更容易放心下单。",
                    "如果先看品牌这一维，我会更倾向" + productRef + "。品牌认知度会更高一些。");
        }
        if (intent != null && intent.isPreferLongTermUse()) {
            return pickVariant(seed,
                    "如果你更看重长期使用体验，我会更偏向" + productRef + "。从稳定性和长期持有角度看会更稳妥。",
                    "按你现在更在意久用省心的思路，我会更偏向" + productRef + "。长期使用会更稳一些。",
                    "如果把长期使用体验放前面看，我会更倾向" + productRef + "。它更像是稳妥型选择。");
        }
        if (intent != null && (intent.getBudgetMin() != null || intent.getBudgetMax() != null) && isWithinBudget(recommended, intent)) {
            return pickVariant(seed,
                    "这两款都不算踩雷，但按你当前预算和需求，我会更偏向" + productRef + "。",
                    "如果按你现在的预算和需求一起看，我会更倾向于" + productRef + "。",
                    "这两款都能买，不过结合你当前预算，我会更偏向" + productRef + "。");
        }
        if (normalizedMessage != null && normalizedMessage.contains("性价比")) {
            return pickVariant(seed,
                    "如果你更看重性价比，我会更偏向" + productRef + "。这款更容易下决定。",
                    "按性价比优先来看，我会更偏向" + productRef + "。这款的决策成本更低。",
                    "如果把性价比放前面，我会更倾向" + productRef + "。整体会更容易下手。");
        }
        return pickVariant(seed,
                "这两款都能买，但按你刚才提到的重点，我会更偏向" + productRef + "。",
                "如果按你刚才最在意的点来选，我会更倾向于" + productRef + "。",
                "这两款都不算踩雷，不过顺着你刚才的需求看，我会更偏向" + productRef + "。");
    }

    private String buildComparisonClosing(ShoppingIntentDTO intent, Product alternative) {
        String productRef = productRef(alternative);
        String seed = firstNonEmpty(alternative == null ? null : alternative.getName(),
                intent == null ? null : intent.getContextMessage(),
                "comparison-closing");
        if (intent != null && intent.isPreferLongTermUse()) {
            return pickVariant(seed,
                    "如果你愿意，我还能继续从续航、做工稳定性和通勤体验这几个角度，帮你和" + productRef + "再细比一轮。",
                    "如果你想继续往下拆，我可以再从续航、稳定性和长期使用成本这几个角度，帮你和" + productRef + "细比一轮。",
                    "如果你愿意，我还可以继续按长期使用、通勤体验和稳定性，帮你和" + productRef + "再过一遍。");
        }
        if (intent != null && intent.isPreferMajorBrand()) {
            return pickVariant(seed,
                    "如果你愿意，我还能继续按品牌生态、预算和使用场景，帮你和" + productRef + "再拆一轮。",
                    "如果你想继续比下去，我可以再从品牌生态、预算带和场景适配这几个角度，帮你和" + productRef + "细拆一轮。",
                    "如果你愿意，我还可以继续按品牌、预算和通勤 / 送礼场景，帮你和" + productRef + "再细看一次。");
        }
        return pickVariant(seed,
                "如果你愿意，我还可以继续按“更适合通勤 / 更适合送礼 / 更值得长期用”这些角度，帮你和" + productRef + "再做一轮更细的判断。",
                "如果你想继续往下比，我还能继续从通勤、送礼和长期使用这几个角度，帮你和" + productRef + "再拆一轮。",
                "如果你愿意，我还可以继续换成“通勤 / 送礼 / 长期使用”这几条线，帮你和" + productRef + "再细比一次。");
    }

    private String buildProductQuestionOpening(ShoppingIntentDTO intent, String normalizedMessage, Product product) {
        String seed = firstNonEmpty(product == null ? null : product.getName(), normalizedMessage, "product-question");
        if (intent != null && intent.isPreferLongTermUse()) {
            return pickVariant(seed,
                    "如果你是在确认“" + product.getName() + "适不适合长期用”，我的判断是：",
                    "如果你重点是在看“" + product.getName() + "能不能久用”，我先给你一个直接判断：");
        }
        if (normalizedMessage != null && containsAny(normalizedMessage, Arrays.asList("怎么样", "好不好"))) {
            return pickVariant(seed,
                    "关于“" + product.getName() + "”，我先给你一句直观结论：",
                    "如果你是在问“" + product.getName() + "到底怎么样”，我先直接给你判断：",
                    "先说结论，关于“" + product.getName() + "”我的看法是：");
        }
        return pickVariant(seed,
                "如果你是在问“" + product.getName() + "值不值得买”，我的直观看法是：",
                "如果你想确认“" + product.getName() + "值不值这个价”，我先给你一个直接判断：",
                "关于“" + product.getName() + "”值不值得买，我先把结论放前面说：");
    }

    private String buildProductQuestionClosing(ShoppingIntentDTO intent, String normalizedMessage, Product product) {
        String seed = firstNonEmpty(product == null ? null : product.getName(),
                intent == null ? null : intent.getContextMessage(),
                "product-question-closing");
        if (intent != null && !intent.getScenes().isEmpty()) {
            return pickVariant(seed,
                    "如果你主要是" + intent.getScenes().get(0) + "场景使用，我也可以继续帮你判断它是不是最合适。",
                    "如果你这次主要是" + intent.getScenes().get(0) + "场景，我还能继续帮你看看它是不是当前最合适的一款。");
        }
        if (intent != null && intent.isPreferLongTermUse()) {
            return pickVariant(seed,
                    "如果你愿意，我还能顺手帮你找两款更适合长期使用的同类商品，一起横向看。",
                    "如果你想继续比，我还能顺手找两款更偏耐用、适合久用的同类商品一起看。",
                    "如果你愿意，我还可以再找两款更适合长期用的同类替代款，帮你横向比一下。");
        }
        if (normalizedMessage != null && containsAny(normalizedMessage, Arrays.asList("价格", "多少钱", "贵吗"))) {
            return pickVariant(seed,
                    "如果你愿意，我还能顺手帮你找两款同价位替代款一起比较。",
                    "如果你想继续看，我还能再找两款同价位替代款给你横向比一下。");
        }
        if (intent != null && intent.isPreferHighSales()) {
            return pickVariant(seed,
                    "如果你愿意，我还能再找两款销量更高的同价位替代款，一起横向比一下。",
                    "如果你想继续比较，我还能补两款销量更高的同价位替代款给你一起看。");
        }
        return pickVariant(seed,
                "如果你愿意，我还能顺手帮你找两款同价位或同类型替代款，一起横向比一下。",
                "如果你还想继续看，我还能再补两款同价位或同类型的替代款给你对比。",
                "如果你愿意，我还可以继续找两款相近价位或同方向的替代款，一起横向过一遍。");
    }

    private String buildFallbackClosing(ShoppingIntentDTO intent) {
        String seed = firstNonEmpty(intent == null ? null : intent.getContextMessage(), "fallback-closing");
        if (intent != null && intent.isPreferAlternatives()) {
            return pickVariant(seed,
                    "如果你还想继续换方向，我可以再按品牌、销量、通勤或长期使用这几个条件重排。",
                    "如果你还想再换一组，我可以继续按预算、品牌或长期使用这几个条件重排。",
                    "如果你想继续换路线，我还能再按销量、品牌或通勤方向给你重新排一轮。");
        }
        if (intent != null && intent.isPreferLongTermUse()) {
            return pickVariant(seed,
                    "你如果告诉我更看重续航、稳定性还是通勤体验，我还能继续帮你把范围收得更小。",
                    "如果你补一句更在意续航、稳定性还是长期使用成本，我还能继续收窄。",
                    "你如果告诉我更在意耐用度、续航还是通勤体验，我还能继续把范围缩小。");
        }
        return pickVariant(seed,
                "你如果告诉我更看重品牌、性价比、送礼体面，还是日常通勤使用，我还能继续帮你把范围收得更小。",
                "如果你补一句更偏品牌、销量、送礼还是通勤场景，我还能继续把范围缩小。",
                "你如果愿意再补一个重点，比如品牌、性价比或通勤使用，我还能继续帮你收窄。");
    }

    private BigDecimal resolveBudgetReference(ShoppingIntentDTO intent, String userMessage) {
        if (intent != null) {
            if (intent.getBudgetMax() != null) {
                return intent.getBudgetMax();
            }
            if (intent.getBudgetMin() != null) {
                return intent.getBudgetMin();
            }
        }
        return extractBudget(userMessage);
    }

    private String productRef(Product product) {
        if (product == null) {
            return "这款商品";
        }
        return "[" + product.getName() + "](product:" + product.getId() + ")";
    }

    private String buildSalesFallbackReason(Product product, String categoryName, BigDecimal budget) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (product.getPrice() != null) {
            if (budget != null && product.getPrice().compareTo(budget) <= 0) {
                reasons.add("价格在预算内");
            } else if (budget != null) {
                reasons.add("价格接近你现在的预算带");
            } else {
                reasons.add("到手价约 " + formatPrice(product.getPrice()) + " 元");
            }
        }
        List<String> highlights = extractProductHighlights(product, categoryName);
        if (!highlights.isEmpty()) {
            reasons.add("卖点偏向 " + String.join("、", highlights));
        }
        if (product.getRating() != null && product.getRating().compareTo(BigDecimal.valueOf(4.6)) >= 0) {
            reasons.add(String.format(Locale.ROOT, "口碑 %.1f", product.getRating()));
        } else if (product.getSalesCount() != null && product.getSalesCount() > 0) {
            reasons.add(product.getSalesCount() >= 1000 ? "销量表现靠前" : "已有稳定成交");
        }
        if (reasons.isEmpty()) {
            reasons.add("属于" + categoryName + "方向");
        }
        return reasons.stream().limit(3).collect(Collectors.joining("；"));
    }

    private String buildIntentSummary(ShoppingIntentDTO intent) {
        List<String> parts = new ArrayList<>();
        if (trimToNull(intent.getCategoryName()) != null) {
            parts.add(intent.getCategoryName());
        } else if (!intent.getTopCategories().isEmpty()) {
            parts.add(intent.getTopCategories().get(0));
        }
        if (!intent.getKeywords().isEmpty()) {
            parts.add(String.join("、", intent.getKeywords().subList(0, Math.min(2, intent.getKeywords().size()))));
        }
        if (!intent.getPreferredBrands().isEmpty()) {
            parts.add(intent.getPreferredBrands().get(0));
        }
        if (!intent.getScenes().isEmpty()) {
            parts.add(intent.getScenes().get(0) + "场景");
        }
        if (intent.isPreferHighSales()) {
            parts.add("销量优先");
        }
        if (intent.isPreferMajorBrand()) {
            parts.add("品牌更稳");
        }
        if (intent.isPreferLongTermUse()) {
            parts.add("更适合长期用");
        }
        if (intent.getBudgetMin() != null && intent.getBudgetMax() != null) {
            parts.add(formatPrice(intent.getBudgetMin()) + "到" + formatPrice(intent.getBudgetMax()) + "元");
        } else if (intent.getBudgetMax() != null) {
            parts.add("预算" + formatPrice(intent.getBudgetMax()) + "元以内");
        } else if (intent.getBudgetMin() != null) {
            parts.add("预算" + formatPrice(intent.getBudgetMin()) + "元以上");
        }
        return parts.isEmpty() ? null : String.join("、", parts);
    }

    private List<String> resolvePersonaStarterCategories(ShoppingIntentDTO intent) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        if (intent != null) {
            if (trimToNull(intent.getCategoryName()) != null) {
                categories.add(intent.getCategoryName());
            }
            categories.addAll(trimAndLimitList(intent.getTopCategories(), 2));
            String inferred = inferCategoryFromTopTags(intent.getTopTags());
            if (trimToNull(inferred) != null) {
                categories.add(inferred);
            }
        }
        return new ArrayList<>(categories);
    }

    private List<String> buildStarterPromptsForCategory(String category) {
        String normalizedCategory = normalizeIntentText(category);
        String label = trimToNull(category);
        if (normalizedCategory == null || label == null) {
            return Collections.emptyList();
        }
        if (containsAny(normalizedCategory, Arrays.asList("手机", "电脑", "笔记本", "平板", "数码", "耳机"))) {
            return Arrays.asList(label + "，预算 500 元以内", "帮我挑个适合送礼的" + label);
        }
        if (containsAny(normalizedCategory, Arrays.asList("护肤", "美妆", "防晒", "个护"))) {
            return Arrays.asList("300 元左右适合通勤的" + label, "帮我挑个更适合送礼的" + label);
        }
        if (containsAny(normalizedCategory, Arrays.asList("食品", "零食", "礼盒", "生鲜", "茶", "咖啡"))) {
            return Arrays.asList("适合送礼的" + label, "300 元以内更值得回购的" + label);
        }
        if (containsAny(normalizedCategory, Arrays.asList("家居", "家电", "厨具", "收纳", "办公"))) {
            return Arrays.asList("通勤和办公都适合的" + label, "预算 500 元左右更实用的" + label);
        }
        if (containsAny(normalizedCategory, Arrays.asList("服饰", "穿搭", "鞋", "外套", "箱包"))) {
            return Arrays.asList("适合通勤的" + label, "帮我挑个更适合送礼的" + label);
        }
        return Arrays.asList("帮我挑个更适合送礼的" + label, "预算 500 元左右的" + label);
    }

    private String inferCategoryFromTopTags(List<String> topTags) {
        if (topTags == null || topTags.isEmpty()) {
            return null;
        }
        for (String tag : topTags) {
            String normalizedTag = normalizeIntentText(tag);
            if (normalizedTag == null) {
                continue;
            }
            if (containsAny(normalizedTag, Arrays.asList("耳机", "手机", "电脑", "平板", "数码", "键盘", "鼠标"))) {
                return "手机数码";
            }
            if (containsAny(normalizedTag, Arrays.asList("护肤", "美妆", "防晒", "补水", "控油"))) {
                return "美妆护肤";
            }
            if (containsAny(normalizedTag, Arrays.asList("零食", "食品", "茶", "咖啡", "生鲜", "礼盒"))) {
                return "食品生鲜";
            }
            if (containsAny(normalizedTag, Arrays.asList("家居", "收纳", "保温", "窗帘", "办公"))) {
                return "家居家装";
            }
            if (containsAny(normalizedTag, Arrays.asList("运动", "跑步", "健身", "户外"))) {
                return "运动户外";
            }
        }
        return null;
    }

    private String firstNonBlank(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "" : price.stripTrailingZeros().toPlainString();
    }

    private BudgetRange extractBudgetRange(String text) {
        String normalized = trimToNull(text);
        if (normalized == null) {
            return null;
        }

        java.util.regex.Matcher rangeMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块|rmb)?\\s*(?:到|至|-|~)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:元|块|rmb)?",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (rangeMatcher.find()) {
            BigDecimal first = new BigDecimal(rangeMatcher.group(1));
            BigDecimal second = new BigDecimal(rangeMatcher.group(2));
            return new BudgetRange(first.min(second), first.max(second));
        }

        java.util.regex.Matcher withinMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块|rmb)?\\s*(?:以内|以下|之内|内)",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (withinMatcher.find()) {
            return new BudgetRange(null, new BigDecimal(withinMatcher.group(1)));
        }

        java.util.regex.Matcher aboveMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块|rmb)?\\s*(?:以上|起|起步)",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (aboveMatcher.find()) {
            return new BudgetRange(new BigDecimal(aboveMatcher.group(1)), null);
        }

        java.util.regex.Matcher aroundMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块|rmb)?\\s*左右",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (aroundMatcher.find()) {
            BigDecimal center = new BigDecimal(aroundMatcher.group(1));
            return new BudgetRange(center.multiply(BigDecimal.valueOf(0.85)), center.multiply(BigDecimal.valueOf(1.15)));
        }

        java.util.regex.Matcher budgetMatcher = java.util.regex.Pattern
                .compile("预算\\s*(\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (budgetMatcher.find()) {
            return new BudgetRange(null, new BigDecimal(budgetMatcher.group(1)));
        }

        return null;
    }

    private static final class BudgetRange {
        private final BigDecimal min;
        private final BigDecimal max;

        private BudgetRange(BigDecimal min, BigDecimal max) {
            this.min = min;
            this.max = max;
        }

        private BigDecimal getMin() {
            return min;
        }

        private BigDecimal getMax() {
            return max;
        }
    }

    private static final class GuideCandidate {
        private final Product product;
        private final double score;
        private final boolean relevant;
        private final boolean withinBudget;
        private final boolean categoryMatched;
        private final boolean personalized;
        private final List<String> matchedTerms;

        private GuideCandidate(Product product,
                               double score,
                               boolean relevant,
                               boolean withinBudget,
                               boolean categoryMatched,
                               boolean personalized,
                               List<String> matchedTerms) {
            this.product = product;
            this.score = score;
            this.relevant = relevant;
            this.withinBudget = withinBudget;
            this.categoryMatched = categoryMatched;
            this.personalized = personalized;
            this.matchedTerms = matchedTerms;
        }

        private Product getProduct() {
            return product;
        }

        private double getScore() {
            return score;
        }

        private boolean isRelevant() {
            return relevant;
        }

        private boolean isWithinBudget() {
            return withinBudget;
        }

        private boolean isCategoryMatched() {
            return categoryMatched;
        }

        private boolean isPersonalized() {
            return personalized;
        }

        private List<String> getMatchedTerms() {
            return matchedTerms;
        }
    }

    private String resolveIdentityReply(String userMessage) {
        if (!isIdentityQuestion(userMessage)) {
            return null;
        }
        return "\u6211\u662f\u5c0f\u4f18\uff0c\u8fd9\u4e2a\u5546\u57ce\u7684\u8d2d\u7269\u52a9\u624b\uff0c\u53ef\u4ee5\u5e2e\u4f60\u63a8\u8350\u5546\u54c1\u3001\u6bd4\u8f83\u5dee\u5f02\u548c\u89e3\u7b54\u8d2d\u7269\u95ee\u9898\u3002";
    }

    private boolean isIdentityQuestion(String userMessage) {
        String normalized = normalizeIntentText(userMessage);
        if (normalized == null) {
            return false;
        }

        return normalized.contains("\u4f60\u662f\u8c01")
                || normalized.contains("\u4ecb\u7ecd\u81ea\u5df1")
                || normalized.contains("\u81ea\u6211\u4ecb\u7ecd")
                || normalized.contains("\u4ecb\u7ecd\u4e00\u4e0b\u81ea\u5df1")
                || normalized.contains("\u4f60\u53eb\u4ec0\u4e48")
                || normalized.contains("\u4f60\u662f\u4ec0\u4e48");
    }

    private String normalizeIntentText(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\s+", "");
    }

    private boolean shouldUseShoppingFallback(String aiReply) {
        String normalized = trimToNull(aiReply);
        if (normalized == null) {
            return true;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.contains("i am claude")
                || lower.contains("made by anthropic");
    }

    private String appendProductCardHint(String reply) {
        String normalized = trimToNull(reply);
        if (normalized == null) {
            return "我也补了几款更贴近需求的商品，下面可以直接点开看详情。";
        }
        if (normalized.contains("点开看详情") || normalized.contains("点卡片")) {
            return normalized;
        }
        return normalized + "\n\n我也补了几款更贴近需求的商品，下面可以直接点开看详情。";
    }

    private Map<String, Object> buildShoppingFallbackResponse(String userMessage, ShoppingIntentDTO intent) {
        Map<Long, String> categoryNames = loadCategoryNameMap();
        List<Product> allActiveProducts = loadActiveProductsForFallback();
        List<Product> activeProducts = constrainProductsToIntentCategory(allActiveProducts, intent, true);
        boolean exactMatchFound = hasExactFallbackMatches(activeProducts, categoryNames, userMessage, intent);
        List<Product> recommended = selectFallbackProducts(activeProducts, categoryNames, userMessage);
        return buildAssistantResult(buildSalesFallbackReply(userMessage, intent, recommended, categoryNames, !exactMatchFound), recommended, intent);
    }

    private List<Product> constrainProductsToIntentCategory(List<Product> products, ShoppingIntentDTO intent, boolean fallbackToAll) {
        if (products == null || products.isEmpty() || intent == null || intent.getCategoryId() == null) {
            return products == null ? Collections.emptyList() : products;
        }

        List<Product> filteredProducts = products.stream()
                .filter(product -> product != null && Objects.equals(product.getCategoryId(), intent.getCategoryId()))
                .collect(Collectors.toList());

        return filteredProducts.isEmpty() && fallbackToAll ? products : filteredProducts;
    }

    private Map<Long, String> loadCategoryNameMap() {
        Object cached = redisUtil.get(CATEGORY_NAME_MAP_CACHE_KEY);
        if (cached instanceof Map) {
            Map<?, ?> cachedMap = (Map<?, ?>) cached;
            Map<Long, String> normalizedCategoryMap = normalizeCategoryNameMap(cachedMap);
            if (!normalizedCategoryMap.isEmpty() || cachedMap.isEmpty()) {
                if (shouldRefreshCategoryNameCache(cachedMap)) {
                    redisUtil.set(CATEGORY_NAME_MAP_CACHE_KEY, normalizedCategoryMap, promptCacheMinutes, TimeUnit.MINUTES);
                }
                return normalizedCategoryMap;
            }
            log.warn("[AI] 分类缓存格式异常，已回退数据库加载: {}", cached.getClass().getName());
        }

        Map<Long, String> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (left, right) -> left, LinkedHashMap::new));
        redisUtil.set(CATEGORY_NAME_MAP_CACHE_KEY, categoryMap, promptCacheMinutes, TimeUnit.MINUTES);
        return categoryMap;
    }

    private Map<Long, String> normalizeCategoryNameMap(Map<?, ?> source) {
        Map<Long, String> normalized = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return normalized;
        }

        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Long categoryId = parseLong(entry.getKey());
            String categoryName = entry.getValue() == null ? null : trimToNull(entry.getValue().toString());
            if (categoryId == null || categoryName == null) {
                continue;
            }
            normalized.put(categoryId, categoryName);
        }
        return normalized;
    }

    private boolean shouldRefreshCategoryNameCache(Map<?, ?> cachedMap) {
        if (cachedMap == null || cachedMap.isEmpty()) {
            return false;
        }

        for (Map.Entry<?, ?> entry : cachedMap.entrySet()) {
            if (!(entry.getKey() instanceof Long)) {
                return true;
            }
            if (entry.getValue() != null && !(entry.getValue() instanceof String)) {
                return true;
            }
        }
        return false;
    }

    private List<Product> loadActiveProductsForFallback() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getSalesCount)
                .last("LIMIT 120"));
    }

    private List<Product> selectFallbackProducts(List<Product> products, Map<Long, String> categoryNames, String userMessage) {
        String normalizedMessage = normalizeIntentText(userMessage);
        BigDecimal budget = extractBudget(userMessage);

        List<Product> sorted = new ArrayList<>(products);
        sorted.sort(Comparator
                .comparingDouble((Product product) -> computeFallbackScore(product, categoryNames.get(product.getCategoryId()), normalizedMessage, budget))
                .reversed());

        List<Product> relevantProducts = sorted.stream()
                .filter(product -> isFallbackRelevantProduct(product, categoryNames.get(product.getCategoryId()), normalizedMessage))
                .collect(Collectors.toList());

        List<Product> candidateProducts = relevantProducts.isEmpty() ? sorted : relevantProducts;

        List<Product> withinBudget = candidateProducts.stream()
                .filter(product -> budget == null || product.getPrice() == null || product.getPrice().compareTo(budget) <= 0)
                .limit(3)
                .collect(Collectors.toList());
        if (!withinBudget.isEmpty()) {
            return withinBudget;
        }

        return candidateProducts.stream().limit(3).collect(Collectors.toList());
    }

    private boolean hasExactFallbackMatches(List<Product> products,
                                            Map<Long, String> categoryNames,
                                            String userMessage,
                                            ShoppingIntentDTO intent) {
        if (products == null || products.isEmpty() || intent == null || !hasEnoughShoppingSignals(intent)) {
            return false;
        }
        String normalizedMessage = normalizeIntentText(firstNonEmpty(intent.getContextMessage(), userMessage));
        for (Product product : products) {
            if (product == null) {
                continue;
            }
            String categoryName = categoryNames.get(product.getCategoryId());
            boolean categoryMatched = intent.getCategoryId() != null && Objects.equals(intent.getCategoryId(), product.getCategoryId());
            boolean keywordMatched = countFallbackKeywordHits(product, categoryName, normalizedMessage) > 0;
            if (categoryMatched || keywordMatched) {
                return true;
            }
        }
        return false;
    }

    private boolean isFallbackRelevantProduct(Product product, String categoryName, String normalizedMessage) {
        return countFallbackKeywordHits(product, categoryName, normalizedMessage) > 0;
    }

    private double computeFallbackScore(Product product, String categoryName, String normalizedMessage, BigDecimal budget) {
        double score = 0;
        int keywordHits = countFallbackKeywordHits(product, categoryName, normalizedMessage);
        score += keywordHits * 30.0;

        BigDecimal rating = product.getRating();
        if (rating != null) {
            score += rating.doubleValue() * 4.0;
        }

        Integer salesCount = product.getSalesCount();
        if (salesCount != null) {
            score += Math.min(salesCount / 1200.0, 15.0);
        }

        if (budget != null && product.getPrice() != null) {
            BigDecimal delta = product.getPrice().subtract(budget);
            if (delta.compareTo(BigDecimal.ZERO) <= 0) {
                score += 22.0;
            } else {
                score -= Math.min(delta.doubleValue() / 18.0, keywordHits > 0 ? 18.0 : 35.0);
            }
        }

        if (keywordHits == 0 && normalizedMessage != null && normalizedMessage.length() > 0) {
            score -= 8.0;
        }

        return score;
    }

    private int countFallbackKeywordHits(Product product, String categoryName, String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isEmpty()) {
            return 0;
        }

        String haystack = normalizeIntentText(buildProductHaystack(product, categoryName));
        if (haystack == null || haystack.isEmpty()) {
            return 0;
        }

        int hits = 0;
        if (normalizedMessage.contains(normalizeIntentText(product.getName()))) {
            hits += 3;
        }
        if (categoryName != null && normalizedMessage.contains(normalizeIntentText(categoryName))) {
            hits += 2;
        }

        for (String keyword : Arrays.asList("耳机", "蓝牙", "降噪", "手机", "数码", "电脑", "办公", "键盘", "鼠标",
                "平板", "空调", "冰箱", "洗衣机", "烤箱", "咖啡", "零食", "巧克力", "图书", "文具",
                "跑鞋", "运动", "护肤", "美妆", "口红", "面膜", "精华", "防晒", "奶粉", "纸尿裤",
                "玩具", "积木", "保温杯", "台灯", "收纳", "窗帘", "地毯")) {
            if (normalizedMessage.contains(keyword) && haystack.contains(keyword)) {
                hits++;
            }
        }

        if (product.getTags() != null) {
            for (String tag : product.getTags()) {
                String normalizedTag = normalizeIntentText(tag);
                if (normalizedTag != null && !normalizedTag.isEmpty() && normalizedMessage.contains(normalizedTag)) {
                    hits += 2;
                }
            }
        }

        return hits;
    }

    private String buildProductHaystack(Product product, String categoryName) {
        List<String> parts = new ArrayList<>();
        if (product.getName() != null) {
            parts.add(product.getName());
        }
        if (product.getDescription() != null) {
            parts.add(product.getDescription());
        }
        if (product.getMerchantName() != null) {
            parts.add(product.getMerchantName());
        }
        if (categoryName != null) {
            parts.add(categoryName);
        }
        if (product.getTags() != null && !product.getTags().isEmpty()) {
            parts.add(String.join(",", product.getTags()));
        }
        return String.join(" ", parts).toLowerCase(Locale.ROOT);
    }

    private BigDecimal extractBudget(String userMessage) {
        String normalized = trimToNull(userMessage);
        if (normalized == null) {
            return null;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块|rmb|￥)?(?:以内|以下|左右)?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        BigDecimal budget = null;
        while (matcher.find()) {
            BigDecimal current = new BigDecimal(matcher.group(1));
            if (budget == null || current.compareTo(budget) < 0) {
                budget = current;
            }
        }
        return budget;
    }

    private String buildShoppingFallbackReply(String userMessage, List<Product> products, Map<Long, String> categoryNames) {
        BigDecimal budget = extractBudget(userMessage);
        if (products.isEmpty()) {
            return "我这边暂时没找到合适的商品，你可以告诉我预算、品类或者品牌偏好，我再帮你细化推荐。";
        }

        boolean hasBudgetMatch = budget == null || products.stream()
                .anyMatch(product -> product.getPrice() == null || product.getPrice().compareTo(budget) <= 0);

        StringBuilder reply = new StringBuilder();
        if (budget != null && !hasBudgetMatch) {
            reply.append("按你目前的预算，我这边没有完全卡在预算内的热门款，不过先给你几款最接近需求的参考：\n");
        } else {
            reply.append("我先给你推荐这几款：\n");
        }

        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            String categoryName = categoryNames.getOrDefault(product.getCategoryId(), "其他");
            reply.append(i + 1)
                    .append(". [")
                    .append(product.getName())
                    .append("](product:")
                    .append(product.getId())
                    .append(")：")
                    .append(buildFallbackReason(product, categoryName, budget))
                    .append("\n");
        }

        reply.append("如果你愿意，我还可以继续按品牌、佩戴方式或者使用场景帮你缩小范围。");
        return reply.toString();
    }

    private String buildFallbackReason(Product product, String categoryName, BigDecimal budget) {
        List<String> reasons = new ArrayList<>();
        if (product.getPrice() != null) {
            if (budget != null && product.getPrice().compareTo(budget) <= 0) {
                reasons.add(String.format(Locale.ROOT, "价格 %.2f 元，在预算内", product.getPrice()));
            } else if (budget != null) {
                reasons.add(String.format(Locale.ROOT, "价格 %.2f 元，略高于你的预算", product.getPrice()));
            } else {
                reasons.add(String.format(Locale.ROOT, "价格 %.2f 元", product.getPrice()));
            }
        }

        if (product.getRating() != null) {
            reasons.add(String.format(Locale.ROOT, "评分 %.1f", product.getRating()));
        }
        if (product.getSalesCount() != null) {
            reasons.add("销量 " + product.getSalesCount());
        }
        reasons.add("分类是" + categoryName);

        return String.join("，", reasons);
    }

    private List<Long> extractProductIds(String text) {
        List<Long> ids = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\(product:(\\d+)\\)")
                .matcher(text);
        while (matcher.find()) {
            try {
                ids.add(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    // ==================== 商家助手 ====================

    @Override
    public Map<String, Object> merchantAssistant(Long merchantId,
                                                 String merchantMessage,
                                                 List<Map<String, String>> history,
                                                 Map<String, Object> draft) {
        Map<String, Object> normalizedDraft = normalizeMerchantDraft(draft);
        Map<String, Object> copyResult = null;
        boolean copyRequest = looksLikeMerchantCopyRequest(merchantMessage, normalizedDraft);

        if (copyRequest) {
            copyResult = generateMerchantProductCopy(merchantId, normalizedDraft);
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msgOf("system", buildMerchantAssistantSystemPrompt(merchantId, normalizedDraft)));
        messages.addAll(sanitizeHistory(history, merchantMessage));
        messages.add(msgOf("user", merchantMessage));

        String reply;
        try {
            reply = callChatCompletion(messages, Math.min(maxTokens, 1400), Math.min(0.6d, temperature));
        } catch (Exception e) {
            log.warn("[AI] merchant assistant fallback triggered: {}", e.getMessage());
            reply = buildMerchantFallbackReply(merchantMessage, normalizedDraft, copyResult);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("merchantVersion", "merchant-copilot-v3");
        result.put("suggestedActions", buildMerchantActionSuggestions(normalizedDraft));
        result.put("draftSummary", buildMerchantDraftSummary(normalizedDraft));
        if (copyResult != null && !copyResult.isEmpty()) {
            result.put("generatedCopy", copyResult);
        }
        return result;
    }

    @Override
    public Map<String, Object> generateMerchantProductCopy(Long merchantId, Map<String, Object> draft) {
        Map<String, Object> normalizedDraft = normalizeMerchantDraft(draft);
        Map<String, Object> aiResult = buildMerchantCopyWithAi(merchantId, normalizedDraft);
        if (aiResult == null || aiResult.isEmpty()) {
            aiResult = buildMerchantCopyFallback(normalizedDraft);
        }

        aiResult.put("source", aiResult.containsKey("source") ? aiResult.get("source") : "fallback");
        aiResult.put("generatedAt", new Date());
        aiResult.put("draftSummary", buildMerchantDraftSummary(normalizedDraft));
        return aiResult;
    }

    private Map<String, Object> buildMerchantCopyWithAi(Long merchantId, Map<String, Object> draft) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiUrl) || !StringUtils.hasText(model)) {
            return null;
        }

        Map<String, Object> catalogContext = buildMerchantCatalogContext(merchantId);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msgOf("system",
                "你是电商商家增长顾问兼文案策划。"
                        + "你必须只输出 JSON，不要输出 Markdown。"
                        + "JSON 字段固定为："
                        + "{\"productName\":\"...\",\"productSubtitle\":\"...\","
                        + "\"sellingPoints\":[\"...\"],\"description\":\"...\","
                        + "\"tags\":[\"...\"],\"recommendedAudience\":\"...\","
                        + "\"customerPitch\":\"...\",\"liveScript\":\"...\","
                        + "\"serviceReply\":\"...\",\"searchKeywords\":[\"...\"],"
                        + "\"marketingHighlights\":[\"...\"]}"
        ));
        messages.add(msgOf("user",
                "请根据下面的商品草稿和商家店铺信息，生成适合中文电商上架页使用的商品文案。"
                        + "要求：标题突出卖点但不能夸张；卖点 3-4 条；详情描述 120-220 字；"
                        + "标签 4-6 个；客服回复口吻自然；直播口播 80-120 字；"
                        + "如果草稿信息不足，请基于已有字段做合理电商化表达，不要编造不存在的硬参数。\n"
                        + "商品草稿=" + JSON.toJSONString(draft)
                        + "\n店铺概况=" + JSON.toJSONString(catalogContext)
        ));

        try {
            String content = callChatCompletion(messages, Math.min(maxTokens, 1600), 0.35d);
            JSONObject json = parseJsonObjectResponse(content);
            if (json == null || json.isEmpty()) {
                return null;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("productName", firstNonEmpty(
                    trimToNull(json.getString("productName")),
                    stringValue(draft.get("name"))
            ));
            result.put("productSubtitle", trimToNull(json.getString("productSubtitle")));
            result.put("sellingPoints", trimAndLimitList(toStringList(json.get("sellingPoints")), 4));
            result.put("description", trimToNull(json.getString("description")));
            result.put("tags", trimAndLimitList(toStringList(json.get("tags")), MERCHANT_COPY_TAG_LIMIT));
            result.put("recommendedAudience", trimToNull(json.getString("recommendedAudience")));
            result.put("customerPitch", trimToNull(json.getString("customerPitch")));
            result.put("liveScript", trimToNull(json.getString("liveScript")));
            result.put("serviceReply", trimToNull(json.getString("serviceReply")));
            result.put("searchKeywords", trimAndLimitList(toStringList(json.get("searchKeywords")), MERCHANT_COPY_TAG_LIMIT));
            result.put("marketingHighlights", trimAndLimitList(toStringList(json.get("marketingHighlights")), 4));
            result.put("source", "ai");
            return result;
        } catch (Exception e) {
            log.warn("[AI] generate merchant copy with AI failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildMerchantCopyFallback(Map<String, Object> draft) {
        String categoryName = firstNonEmpty(stringValue(draft.get("categoryName")), "品质好物");
        String productName = firstNonEmpty(
                stringValue(draft.get("name")),
                joinNonEmpty(" ", stringValue(draft.get("targetAudience")), stringValue(draft.get("primaryKeyword")), categoryName)
        );
        String subtitle = joinNonEmpty("，",
                firstNonEmpty(stringValue(draft.get("targetAudience")), "适合电商主推人群"),
                firstNonEmpty(stringValue(draft.get("tone")), "突出质价比与购买理由")
        );
        List<String> sellingPoints = new ArrayList<>();
        sellingPoints.add(firstNonEmpty(stringValue(draft.get("primaryKeyword")), "核心卖点突出"));
        if (StringUtils.hasText(stringValue(draft.get("priceText")))) {
            sellingPoints.add("价格带清晰，方便用户快速决策");
        }
        if (!toStringList(draft.get("providedSellingPoints")).isEmpty()) {
            sellingPoints.addAll(toStringList(draft.get("providedSellingPoints")));
        }
        if (!toStringList(draft.get("keywords")).isEmpty()) {
            sellingPoints.add("覆盖用户搜索词：" + String.join("、", toStringList(draft.get("keywords")).subList(0,
                    Math.min(2, toStringList(draft.get("keywords")).size()))));
        }
        sellingPoints = trimAndLimitList(sellingPoints, 4);

        List<String> tags = new ArrayList<>(toStringList(draft.get("keywords")));
        if (StringUtils.hasText(categoryName)) {
            tags.add(categoryName);
        }
        if (StringUtils.hasText(stringValue(draft.get("targetAudience")))) {
            tags.add(stringValue(draft.get("targetAudience")));
        }
        if (StringUtils.hasText(stringValue(draft.get("primaryKeyword")))) {
            tags.add(stringValue(draft.get("primaryKeyword")));
        }
        tags = trimAndLimitList(tags, MERCHANT_COPY_TAG_LIMIT);

        String description = String.format(Locale.ROOT,
                "%s主打%s，适合%s。页面表达建议围绕“为什么值得买、适合谁、下单后能获得什么”来展开，先用一句话讲清核心卖点，再补充场景、价格带与购买理由，提升点击和转化。",
                productName,
                firstNonEmpty(stringValue(draft.get("primaryKeyword")), "实用体验"),
                firstNonEmpty(stringValue(draft.get("targetAudience")), "目标用户")
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productName", productName);
        result.put("productSubtitle", subtitle);
        result.put("sellingPoints", sellingPoints);
        result.put("description", description);
        result.put("tags", tags);
        result.put("recommendedAudience", firstNonEmpty(stringValue(draft.get("targetAudience")), "适合正在寻找同类商品的高意向用户"));
        result.put("customerPitch", "这款商品更适合想快速决策的用户，页面首屏建议直接突出核心卖点、适用场景和价格理由。");
        result.put("liveScript", "这款我们建议主打“核心卖点 + 使用场景 + 下单理由”的讲法，先让用户听懂它适合谁，再强调为什么现在买更划算。");
        result.put("serviceReply", "您好，这款商品主打" + firstNonEmpty(stringValue(draft.get("primaryKeyword")), "核心体验") + "，如果您告诉我预算或使用场景，我也可以继续帮您做更细的推荐。");
        result.put("searchKeywords", tags);
        result.put("marketingHighlights", Arrays.asList("首屏突出卖点", "详情页强调场景", "客服话术保持转化导向"));
        result.put("source", "fallback");
        return result;
    }

    private boolean looksLikeMerchantCopyRequest(String merchantMessage, Map<String, Object> draft) {
        String normalized = normalizeIntentText(merchantMessage);
        if (normalized == null) {
            return draft != null && !draft.isEmpty();
        }
        return containsAny(normalized, MERCHANT_COPY_HINTS)
                || containsAny(normalized, MERCHANT_OPERATE_HINTS)
                || StringUtils.hasText(stringValue(draft.get("name")))
                || StringUtils.hasText(stringValue(draft.get("description")));
    }

    private String buildMerchantAssistantSystemPrompt(Long merchantId, Map<String, Object> draft) {
        Map<String, Object> catalogContext = buildMerchantCatalogContext(merchantId);
        return "你是商家运营助手，专门帮助商家完成商品上架、标题优化、卖点提炼、客服回复和活动表达。"
                + "你必须始终使用简体中文，回答直接、能落地，优先给出可执行建议。"
                + "如果商家正在准备上架文案，请从标题、卖点、详情、标签、客服话术几个角度给建议。"
                + "\n当前商品草稿：" + JSON.toJSONString(draft)
                + "\n当前店铺概况：" + JSON.toJSONString(catalogContext);
    }

    private String buildMerchantFallbackReply(String merchantMessage,
                                              Map<String, Object> draft,
                                              Map<String, Object> copyResult) {
        StringBuilder reply = new StringBuilder();
        reply.append("我先按商家上架助手的思路给你一个可直接落地的方向：");
        if (copyResult != null && !copyResult.isEmpty()) {
            reply.append("\n1. 标题先突出【").append(firstNonEmpty(stringValue(copyResult.get("productName")), "核心商品名")).append("】。");
            List<String> sellingPoints = toStringList(copyResult.get("sellingPoints"));
            if (!sellingPoints.isEmpty()) {
                reply.append("\n2. 卖点可以优先讲：").append(String.join("；", sellingPoints));
            }
            reply.append("\n3. 详情页建议按“适合谁-解决什么问题-为什么值得买”来写。");
        } else {
            reply.append("\n1. 先明确目标客群、核心卖点和价格带。");
            reply.append("\n2. 标题尽量用“品类词 + 关键卖点 + 使用场景”的结构。");
            reply.append("\n3. 详情文案重点讲购买理由，不要只堆参数。");
        }
        if (trimToNull(merchantMessage) != null) {
            reply.append("\n如果你愿意，我还能继续根据你刚才这句“").append(trimToNull(merchantMessage)).append("”往下细化。");
        }
        return reply.toString();
    }

    private List<String> buildMerchantActionSuggestions(Map<String, Object> draft) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        actions.add("生成商品标题");
        actions.add("生成卖点文案");
        actions.add("生成客服回复");
        if (!StringUtils.hasText(stringValue(draft.get("targetAudience")))) {
            actions.add("补充目标人群");
        } else {
            actions.add("优化直播口播");
        }
        return actions.stream().limit(MERCHANT_ACTION_LIMIT).collect(Collectors.toList());
    }

    private Map<String, Object> normalizeMerchantDraft(Map<String, Object> rawDraft) {
        Map<String, Object> source = rawDraft == null ? Collections.emptyMap() : rawDraft;
        Map<String, Object> draft = new LinkedHashMap<>();

        Long categoryId = parseLong(source.get("categoryId"));
        String categoryName = trimToNull(stringValue(source.get("categoryName")));
        if (categoryId != null && categoryName == null) {
            Category category = categoryMapper.selectById(categoryId);
            categoryName = category == null ? null : trimToNull(category.getName());
        }

        List<String> keywordPool = new ArrayList<>();
        keywordPool.addAll(toStringList(source.get("keywords")));
        keywordPool.addAll(toStringList(source.get("tags")));
        keywordPool.addAll(toStringList(source.get("searchKeywords")));

        String name = trimToNull(stringValue(source.get("name")));
        String description = trimToNull(stringValue(source.get("description")));
        String targetAudience = firstNonEmpty(
                trimToNull(stringValue(source.get("targetAudience"))),
                trimToNull(stringValue(source.get("audience"))),
                trimToNull(stringValue(source.get("audienceNote")))
        );
        String tone = firstNonEmpty(trimToNull(stringValue(source.get("tone"))), "专业种草");
        BigDecimal price = safeBigDecimal(source.get("price"));
        BigDecimal originalPrice = safeBigDecimal(source.get("originalPrice"));
        String priceText = price == null ? null : formatPrice(price) + "元";
        String primaryKeyword = !keywordPool.isEmpty()
                ? keywordPool.get(0)
                : firstNonEmpty(categoryName, trimToNull(stringValue(source.get("highlight"))), trimToNull(stringValue(source.get("sellingPoint"))));

        draft.put("name", name);
        draft.put("categoryId", categoryId);
        draft.put("categoryName", categoryName);
        draft.put("description", description);
        draft.put("targetAudience", targetAudience);
        draft.put("tone", tone);
        draft.put("price", price);
        draft.put("originalPrice", originalPrice);
        draft.put("priceText", priceText);
        draft.put("stock", source.get("stock"));
        draft.put("primaryKeyword", primaryKeyword);
        draft.put("keywords", trimAndLimitList(keywordPool, MERCHANT_COPY_TAG_LIMIT));
        draft.put("providedSellingPoints", trimAndLimitList(toStringList(source.get("sellingPoints")), 4));
        draft.put("mainImage", trimToNull(stringValue(source.get("mainImage"))));
        draft.put("detailImages", trimAndLimitList(toStringList(source.get("detailImages")), 4));
        return draft;
    }

    private Map<String, Object> buildMerchantCatalogContext(Long merchantId) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (merchantId == null) {
            return context;
        }

        try {
            List<Product> merchantProducts = productMapper.selectList(
                    new LambdaQueryWrapper<Product>()
                            .eq(Product::getMerchantId, merchantId)
                            .orderByDesc(Product::getSalesCount)
            );
            context.put("productCount", merchantProducts.size());
            context.put("onShelfCount", merchantProducts.stream().filter(item -> item.getStatus() != null && item.getStatus() == 1).count());
            context.put("topProducts", merchantProducts.stream()
                    .limit(3)
                    .map(Product::getName)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList()));
            context.put("topTags", merchantProducts.stream()
                    .filter(Objects::nonNull)
                    .flatMap(item -> item.getTags() == null ? java.util.stream.Stream.empty() : item.getTags().stream())
                    .filter(StringUtils::hasText)
                    .collect(Collectors.groupingBy(item -> item, LinkedHashMap::new, Collectors.counting()))
                    .entrySet()
                    .stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(4)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.warn("[AI] build merchant catalog context failed: {}", e.getMessage());
        }
        return context;
    }

    private String buildMerchantDraftSummary(Map<String, Object> draft) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(stringValue(draft.get("name")))) {
            parts.add("商品名：" + stringValue(draft.get("name")));
        }
        if (StringUtils.hasText(stringValue(draft.get("categoryName")))) {
            parts.add("分类：" + stringValue(draft.get("categoryName")));
        }
        if (StringUtils.hasText(stringValue(draft.get("targetAudience")))) {
            parts.add("人群：" + stringValue(draft.get("targetAudience")));
        }
        if (StringUtils.hasText(stringValue(draft.get("priceText")))) {
            parts.add("价格：" + stringValue(draft.get("priceText")));
        }
        if (!toStringList(draft.get("keywords")).isEmpty()) {
            parts.add("关键词：" + String.join("、", toStringList(draft.get("keywords"))));
        }
        return parts.isEmpty() ? "当前草稿信息较少，建议先补充品类、卖点和目标人群" : String.join(" | ", parts);
    }

    // ==================== 评价摘要 ====================

    @Override
    public String reviewSummary(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        List<ProductReview> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getProductId, productId)
                        .eq(ProductReview::getStatus, 1)
                        .orderByDesc(ProductReview::getCreateTime)
                        .last("LIMIT 50"));

        if (reviews.isEmpty()) {
            return "该商品暂无用户评价。";
        }

        BigDecimal avgRating = reviewMapper.selectAvgRating(productId);
        int totalCount = reviewMapper.selectReviewCount(productId);
        ProductReview latestReview = reviews.get(0);
        String latestReviewTime = latestReview.getCreateTime() == null ? "none" : latestReview.getCreateTime().toString();
        String cacheKey = REVIEW_SUMMARY_CACHE_PREFIX + productId + ":" + totalCount + ":" + latestReviewTime;
        try {
            Object cached = redisUtil.get(cacheKey);
            if (cached != null) {
                return cached.toString();
            }
        } catch (Exception e) {
            log.warn("[AI] 评价摘要缓存读取失败 productId={}: {}", productId, e.getMessage());
        }

        StringBuilder reviewTexts = new StringBuilder();
        for (ProductReview r : reviews) {
            reviewTexts.append(String.format("- [%d星] %s\n",
                    r.getRating(), r.getContent() != null ? r.getContent() : "好评"));
        }

        String systemPrompt = "你是一个电商商品评价分析助手。请根据以下用户评价，生成一段简洁的评价摘要（150字以内）。\n"
                + "要求：\n"
                + "1. 总结用户普遍认可的优点\n"
                + "2. 总结用户提到的不足之处\n"
                + "3. 给出一句综合评价\n"
                + "4. 使用简体中文，语气客观中立\n";

        String userContent = String.format("商品名称：%s\n平均评分：%.1f（共%d条评价）\n\n用户评价：\n%s",
                product.getName(), avgRating, totalCount, reviewTexts);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msgOf("system", systemPrompt));
        messages.add(msgOf("user", userContent));

        String summary = callChatCompletion(messages);

        try {
            redisUtil.set(cacheKey, summary, REVIEW_SUMMARY_CACHE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[AI] 评价摘要缓存写入失败: {}", e.getMessage());
        }

        return summary;
    }

    // ==================== 商品问答 ====================

    @Override
    public String productQA(Long productId, String question) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        List<ProductReview> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getProductId, productId)
                        .eq(ProductReview::getStatus, 1)
                        .orderByDesc(ProductReview::getRating)
                        .last("LIMIT 20"));

        StringBuilder context = new StringBuilder();
        context.append(String.format("商品名称：%s\n", product.getName()));
        if (product.getDescription() != null) {
            context.append(String.format("商品描述：%s\n", product.getDescription()));
        }
        context.append(String.format("价格：%.2f 元\n", product.getPrice()));
        if (product.getTags() != null && !product.getTags().isEmpty()) {
            context.append(String.format("标签：%s\n", String.join(", ", product.getTags())));
        }
        if (product.getRating() != null) {
            context.append(String.format("评分：%.1f\n", product.getRating()));
        }
        if (product.getSalesCount() != null) {
            context.append(String.format("销量：%d 件\n", product.getSalesCount()));
        }

        if (!reviews.isEmpty()) {
            context.append("\n用户评价摘录：\n");
            for (ProductReview r : reviews) {
                if (r.getContent() != null && !r.getContent().isEmpty()) {
                    context.append(String.format("- [%d星] %s\n", r.getRating(), r.getContent()));
                }
            }
        }

        String systemPrompt = "你是一个电商商品智能客服。根据以下商品信息回答用户的问题。\n"
                + "规则：\n"
                + "1. 只根据已知信息回答，不要编造\n"
                + "2. 如果信息不足以回答，如实告知并建议用户咨询店铺客服\n"
                + "3. 回答简洁明了，不超过200字\n"
                + "4. 使用简体中文\n";

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msgOf("system", systemPrompt));
        messages.add(msgOf("user", "以下是商品信息：\n" + context + "\n\n用户问题：" + question));

        return callChatCompletion(messages);
    }

    @Override
    public String customerSupportReply(Long userId,
                                       Map<String, Object> conversationContext,
                                       List<Map<String, Object>> history,
                                       String userMessage) {
        String message = trimToNull(userMessage);
        if (message == null) {
            throw new BusinessException("消息不能为空");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msgOf("system",
                "你是电商平台的 AI 客服。"
                        + "目标是先帮助用户定位问题并给出清晰下一步。"
                        + "请遵守："
                        + "1. 使用简体中文，语气专业、克制、友好；"
                        + "2. 回答 45~100 字，最多 3 句，优先给可执行步骤；"
                        + "3. 不编造订单状态和平台规则；"
                        + "4. 涉及退款/赔付/投诉争议时明确提示可转人工客服；"
                        + "5. 如果信息不足，直接说明缺什么信息。"));

        String contextSummary = buildCustomerSupportContextSummary(conversationContext);
        if (StringUtils.hasText(contextSummary)) {
            messages.add(msgOf("system", "会话上下文：\n" + contextSummary));
        }

        messages.addAll(sanitizeCustomerSupportHistory(history, message));
        messages.add(msgOf("user", message));

        String reply = callChatCompletion(
                messages,
                Math.min(maxTokens, CUSTOMER_SUPPORT_REPLY_MAX_TOKENS),
                Math.min(0.45d, temperature)
        );
        String normalized = trimToNull(reply);
        if (normalized == null) {
            throw new BusinessException("AI 返回为空");
        }
        return normalized;
    }

    @Override
    public Map<String, String> summarizeCustomerServiceHandoff(Long userId,
                                                               Map<String, Object> conversationContext,
                                                               List<Map<String, Object>> transcript) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msgOf("system",
                "你是电商平台客服质检助手。请将会话摘要成给人工客服的接手信息。"
                        + "仅输出 JSON，不要任何额外文本。"
                        + "JSON 格式："
                        + "{\"issueSummary\":\"\",\"issueDetail\":\"\",\"suggestedAction\":\"\"}。"
                        + "要求："
                        + "1. issueSummary 20~40字，聚焦核心诉求；"
                        + "2. issueDetail 60~160字，包含已知事实、用户诉求、风险点；"
                        + "3. suggestedAction 30~100字，给人工客服下一步处理建议。"));

        String contextSummary = buildCustomerSupportContextSummary(conversationContext);
        String transcriptSummary = buildTranscriptSummary(transcript);
        messages.add(msgOf("user",
                "请基于以下信息生成交接摘要：\n\n"
                        + "【会话上下文】\n" + (contextSummary == null ? "无" : contextSummary) + "\n\n"
                        + "【会话记录】\n" + (transcriptSummary == null ? "无" : transcriptSummary)));

        String raw = callChatCompletion(
                messages,
                Math.min(maxTokens, CUSTOMER_SUPPORT_SUMMARY_MAX_TOKENS),
                0.2d
        );
        return parseHandoffSummary(raw);
    }

    private static Map<String, String> msgOf(String role, String content) {
        Map<String, String> m = new HashMap<>(4);
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private List<Map<String, String>> sanitizeCustomerSupportHistory(List<Map<String, Object>> history,
                                                                     String currentUserMessage) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, String>> sanitized = new ArrayList<>();
        for (Map<String, Object> item : history) {
            if (item == null) {
                continue;
            }
            String content = stringValue(item.get("content"));
            if (content == null) {
                continue;
            }
            String rawRole = firstNonEmpty(stringValue(item.get("role")), stringValue(item.get("senderRole")));
            String normalizedRole = "user".equals(rawRole) ? "user" : "assistant";
            if ("system".equals(rawRole)) {
                content = "系统提示：" + content;
            } else if ("ai".equals(rawRole)) {
                content = "AI客服：" + content;
            } else if ("admin".equals(rawRole)) {
                content = "人工客服：" + content;
            } else if ("merchant".equals(rawRole)) {
                content = "商家客服：" + content;
            }
            sanitized.add(msgOf(normalizedRole, content));
        }

        String normalizedCurrentMessage = trimToNull(currentUserMessage);
        if (!sanitized.isEmpty() && normalizedCurrentMessage != null) {
            Map<String, String> last = sanitized.get(sanitized.size() - 1);
            if ("user".equals(last.get("role")) && normalizedCurrentMessage.equals(trimToNull(last.get("content")))) {
                sanitized.remove(sanitized.size() - 1);
            }
        }

        if (sanitized.size() > CUSTOMER_SUPPORT_HISTORY_LIMIT) {
            return new ArrayList<>(sanitized.subList(sanitized.size() - CUSTOMER_SUPPORT_HISTORY_LIMIT, sanitized.size()));
        }
        return sanitized;
    }

    private String buildCustomerSupportContextSummary(Map<String, Object> conversationContext) {
        if (conversationContext == null || conversationContext.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        String status = stringValue(conversationContext.get("status"));
        if (status != null) {
            lines.add("会话状态：" + status);
        }
        String priority = stringValue(conversationContext.get("priority"));
        if (priority != null) {
            lines.add("优先级：" + priority);
        }

        Object contextObject = conversationContext.get("context");
        if (contextObject instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) contextObject;
            Object orderObject = context.get("order");
            if (orderObject instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> order = (Map<String, Object>) orderObject;
                lines.add("关联订单：" + firstNonEmpty(stringValue(order.get("orderNo")), "#" + stringValue(order.get("id"))));
                String statusText = stringValue(order.get("status"));
                if (statusText != null) {
                    lines.add("订单状态：" + statusText);
                }
            }
            Object productObject = context.get("product");
            if (productObject instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> product = (Map<String, Object>) productObject;
                String productName = firstNonEmpty(stringValue(product.get("name")), "#" + stringValue(product.get("id")));
                if (productName != null) {
                    lines.add("关联商品：" + productName);
                }
            }
        }

        Object queueObject = conversationContext.get("queue");
        if (queueObject instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> queue = (Map<String, Object>) queueObject;
            String position = stringValue(queue.get("position"));
            String waitMinutes = stringValue(queue.get("estimatedWaitMinutes"));
            if (position != null) {
                lines.add("排队位置：" + position + "，预计等待：" + (waitMinutes == null ? "--" : waitMinutes) + "分钟");
            }
        }

        if (lines.isEmpty()) {
            return null;
        }
        return String.join("\n", lines);
    }

    private String buildTranscriptSummary(List<Map<String, Object>> transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> records = transcript;
        if (transcript.size() > CUSTOMER_SUPPORT_TRANSCRIPT_LIMIT) {
            records = transcript.subList(transcript.size() - CUSTOMER_SUPPORT_TRANSCRIPT_LIMIT, transcript.size());
        }
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> item : records) {
            if (item == null) {
                continue;
            }
            String role = firstNonEmpty(stringValue(item.get("senderRole")), stringValue(item.get("role")), "unknown");
            String content = stringValue(item.get("content"));
            if (content == null) {
                continue;
            }
            String time = stringValue(item.get("createTime"));
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append('[').append(role).append(']');
            if (time != null) {
                builder.append('[').append(time).append(']');
            }
            builder.append(' ').append(content);
        }
        String result = trimToNull(builder.toString());
        return result == null ? null : result;
    }

    private Map<String, String> parseHandoffSummary(String raw) {
        String text = trimToNull(raw);
        if (text == null) {
            return buildFallbackHandoffSummary();
        }
        JSONObject parsed = null;
        try {
            parsed = JSON.parseObject(text);
        } catch (Exception ignored) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    parsed = JSON.parseObject(text.substring(start, end + 1));
                } catch (Exception ignoredAgain) {
                    parsed = null;
                }
            }
        }
        if (parsed == null) {
            return buildFallbackHandoffSummary();
        }

        Map<String, String> result = new LinkedHashMap<>();
        String issueSummary = trimToNull(parsed.getString("issueSummary"));
        String issueDetail = trimToNull(parsed.getString("issueDetail"));
        String suggestedAction = trimToNull(parsed.getString("suggestedAction"));
        result.put("issueSummary", issueSummary == null ? "用户请求转人工客服，待人工核实处理" : issueSummary);
        result.put("issueDetail", issueDetail == null ? "AI 未解析出完整会话摘要，建议人工先阅读最近聊天记录。" : issueDetail);
        result.put("suggestedAction", suggestedAction == null ? "优先确认用户诉求和订单信息，再给出处理方案。" : suggestedAction);
        return result;
    }

    private Map<String, String> buildFallbackHandoffSummary() {
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("issueSummary", "用户请求转人工客服，待人工核实处理");
        fallback.put("issueDetail", "AI 未解析出完整会话摘要，建议人工先阅读最近聊天记录。");
        fallback.put("suggestedAction", "优先确认用户诉求和订单信息，再给出处理方案。");
        return fallback;
    }

    // ==================== API 调用（兼容各类中转代理） ====================

    private List<Map<String, String>> sanitizeHistory(List<Map<String, String>> history, String currentUserMessage) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, String>> sanitized = new ArrayList<>();
        for (Map<String, String> item : history) {
            if (item == null) {
                continue;
            }

            String role = trimToNull(item.get("role"));
            String content = trimToNull(item.get("content"));
            if (role == null || content == null) {
                continue;
            }

            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }

            sanitized.add(msgOf(role, content));
        }

        String normalizedCurrentMessage = trimToNull(currentUserMessage);
        if (!sanitized.isEmpty() && normalizedCurrentMessage != null) {
            Map<String, String> last = sanitized.get(sanitized.size() - 1);
            if ("user".equals(last.get("role")) && normalizedCurrentMessage.equals(last.get("content"))) {
                sanitized.remove(sanitized.size() - 1);
            }
        }

        if (sanitized.size() > MAX_HISTORY_MESSAGES) {
            return new ArrayList<>(sanitized.subList(sanitized.size() - MAX_HISTORY_MESSAGES, sanitized.size()));
        }

        return sanitized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return trimToNull(String.valueOf(value));
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        if (value instanceof Collection<?>) {
            for (Object item : (Collection<?>) value) {
                String normalized = stringValue(item);
                if (normalized != null) {
                    values.add(normalized);
                }
            }
            return trimAndLimitList(values, Integer.MAX_VALUE);
        }

        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                String normalized = stringValue(java.lang.reflect.Array.get(value, i));
                if (normalized != null) {
                    values.add(normalized);
                }
            }
            return trimAndLimitList(values, Integer.MAX_VALUE);
        }

        String text = stringValue(value);
        if (text == null) {
            return Collections.emptyList();
        }

        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                JSONArray array = JSON.parseArray(text);
                if (array != null) {
                    return toStringList(array);
                }
            } catch (Exception ignored) {
                // Fall through to delimiter-based parsing.
            }
        }

        return trimAndLimitList(Arrays.asList(text.split("[,，;；\\n]+")), Integer.MAX_VALUE);
    }

    private BigDecimal safeBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> trimAndLimitList(List<String> values, int limit) {
        if (values == null || values.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                unique.add(normalized);
            }
            if (unique.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private JSONObject parseJsonObjectResponse(String content) {
        String normalized = trimToNull(content);
        if (normalized == null) {
            return null;
        }

        if (normalized.startsWith("```")) {
            normalized = normalized
                    .replaceFirst("^```json\\s*", "")
                    .replaceFirst("^```\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        try {
            return JSON.parseObject(normalized);
        } catch (Exception ignored) {
            int start = normalized.indexOf('{');
            int end = normalized.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return JSON.parseObject(normalized.substring(start, end + 1));
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private String firstNonEmpty(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String joinNonEmpty(String delimiter, String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        List<String> normalizedValues = new ArrayList<>();
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                normalizedValues.add(normalized);
            }
        }
        if (normalizedValues.isEmpty()) {
            return null;
        }
        return String.join(delimiter == null ? "" : delimiter, normalizedValues);
    }

    private String callChatCompletion(List<Map<String, String>> messages) {
        return callChatCompletion(messages, maxTokens, temperature);
    }

    private String callChatCompletion(List<Map<String, String>> messages, int requestMaxTokens, double requestTemperature) {
        validateAiConfiguration();
        String url = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
        url += "v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<Map<String, String>> finalMessages = new ArrayList<>();
        List<String> systemMessages = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            if (msg == null) {
                continue;
            }

            String role = trimToNull(msg.get("role"));
            String content = trimToNull(msg.get("content"));
            if (role == null || content == null) {
                continue;
            }

            if ("system".equals(role)) {
                systemMessages.add(content);
                continue;
            }

            if ("user".equals(role) || "assistant".equals(role)) {
                finalMessages.add(msgOf(role, content));
            }
        }

        if (!systemMessages.isEmpty()) {
            finalMessages.add(0, msgOf("system", String.join("\n\n", systemMessages)));
        }

        try {
            return doChatCompletion(url, headers, finalMessages, requestMaxTokens, requestTemperature);
        } catch (HttpStatusCodeException e) {
            if (!systemMessages.isEmpty() && shouldRetryWithoutSystem(e)) {
                log.warn("[AI] system role fallback triggered, status={}, body={}",
                        e.getStatusCode(), e.getResponseBodyAsString());
                return doChatCompletion(url, headers, mergeSystemMessage(finalMessages), requestMaxTokens, requestTemperature);
            }
            throw e;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] 调用异常: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务连接失败: " + e.getMessage());
        }
    }

    private String doChatCompletion(String url, HttpHeaders headers, List<Map<String, String>> messages,
                                    int requestMaxTokens, double requestTemperature) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", requestMaxTokens);
        body.put("temperature", requestTemperature);

        HttpEntity<String> request = new HttpEntity<>(JSON.toJSONString(body), headers);

        log.info("[AI] 请求 model={}, messages={}, maxTokens={}, temperature={}, url={}",
                model, messages.size(), requestMaxTokens, requestTemperature, url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            JSONObject json = JSON.parseObject(response.getBody());
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                String content = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                log.info("[AI] 响应成功，长度={}", content != null ? content.length() : 0);
                return content;
            }
        }

        log.error("[AI] 调用失败，状态码={}, body={}", response.getStatusCode(), response.getBody());
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    private void validateAiConfiguration() {
        if (!StringUtils.hasText(apiUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            throw new BusinessException("AI 服务未配置，请先设置 AI_API_URL、AI_API_KEY 和 AI_MODEL。");
        }
    }

    private boolean shouldRetryWithoutSystem(HttpStatusCodeException e) {
        return e.getStatusCode() == HttpStatus.BAD_REQUEST
                || e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY;
    }

    private List<Map<String, String>> mergeSystemMessage(List<Map<String, String>> messages) {
        List<Map<String, String>> merged = new ArrayList<>();
        StringBuilder systemContent = new StringBuilder();

        for (Map<String, String> msg : messages) {
            if ("system".equals(msg.get("role"))) {
                if (systemContent.length() > 0) {
                    systemContent.append("\n\n");
                }
                systemContent.append(msg.get("content"));
                continue;
            }

            merged.add(msgOf(msg.get("role"), msg.get("content")));
        }

        if (systemContent.length() == 0) {
            return merged;
        }

        if (merged.isEmpty()) {
            merged.add(msgOf("user", systemContent.toString()));
            return merged;
        }

        Map<String, String> first = merged.get(0);
        if ("user".equals(first.get("role"))) {
            first.put("content", systemContent + "\n\n---\n\n" + first.get("content"));
        } else {
            merged.add(0, msgOf("user", systemContent.toString()));
        }

        return merged;
    }

    private String callChatCompletionLegacy(List<Map<String, String>> messages) {
        String url = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
        url += "v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String systemContent = null;
        List<Map<String, String>> finalMessages = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            if ("system".equals(msg.get("role"))) {
                systemContent = msg.get("content");
            } else {
                finalMessages.add(new HashMap<>(msg));
            }
        }

        if (systemContent != null) {
            if (finalMessages.isEmpty()) {
                finalMessages.add(msgOf("user", systemContent));
            } else {
                Map<String, String> first = finalMessages.get(0);
                first.put("content", systemContent + "\n\n---\n\n" + first.get("content"));
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", finalMessages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);

        HttpEntity<String> request = new HttpEntity<>(JSON.toJSONString(body), headers);

        try {
            log.info("[AI] 请求 model={}, messages={}, url={}", model, finalMessages.size(), url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject json = JSON.parseObject(response.getBody());
                JSONArray choices = json.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    String content = choices.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                    log.info("[AI] 响应成功，长度={}", content != null ? content.length() : 0);
                    return content;
                }
            }

            log.error("[AI] 调用失败，状态码={}, body={}", response.getStatusCode(), response.getBody());
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] 调用异常: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务连接失败: " + e.getMessage());
        }
    }

    private String loadPromptCategorySummary(Map<Long, String> categoryNames) {
        Object cached = redisUtil.get(PROMPT_CATEGORY_SUMMARY_CACHE_KEY);
        if (cached instanceof String) {
            return (String) cached;
        }

        String summary = categoryNames.entrySet().stream()
                .map(entry -> entry.getKey() + "-" + entry.getValue())
                .collect(Collectors.joining(", "));
        redisUtil.set(PROMPT_CATEGORY_SUMMARY_CACHE_KEY, summary, promptCacheMinutes, TimeUnit.MINUTES);
        return summary;
    }

    private String loadPromptProductSummary(Map<Long, String> categoryNames) {
        Object cached = redisUtil.get(PROMPT_PRODUCT_SUMMARY_CACHE_KEY);
        if (cached instanceof String) {
            return (String) cached;
        }

        String summary = "";
        try {
            List<Product> products = productMapper.selectList(
                    new LambdaQueryWrapper<Product>()
                            .eq(Product::getStatus, 1)
                            .select(Product::getId, Product::getName, Product::getPrice,
                                    Product::getCategoryId, Product::getRating, Product::getSalesCount)
                            .orderByDesc(Product::getSalesCount)
                            .last("LIMIT " + Math.max(promptProductLimit, 1)));
            summary = products.stream()
                    .map(product -> formatPromptProductLine(product, categoryNames))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("[AI] 加载提示词商品摘要失败: {}", e.getMessage());
        }

        redisUtil.set(PROMPT_PRODUCT_SUMMARY_CACHE_KEY, summary, promptCacheMinutes, TimeUnit.MINUTES);
        return summary;
    }

    private String formatPromptProductLine(Product product, Map<Long, String> categoryNames) {
        String categoryName = categoryNames.getOrDefault(product.getCategoryId(), "其他");
        return String.format("- ID:%d「%s」 ¥%.0f %s 评分%.1f 销量%d",
                product.getId(),
                product.getName(),
                product.getPrice(),
                categoryName,
                product.getRating() != null ? product.getRating() : BigDecimal.valueOf(5.0),
                product.getSalesCount() != null ? product.getSalesCount() : 0);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> limitCollection(Collection<String> values, int limit) {
        if (values == null || values.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        return values.stream().filter(Objects::nonNull).limit(limit).collect(Collectors.toList());
    }
}
