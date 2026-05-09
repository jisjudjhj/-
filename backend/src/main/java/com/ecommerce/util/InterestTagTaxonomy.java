package com.ecommerce.util;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Expands raw product/user tags into a finer interest taxonomy used by recommendation ranking.
 */
public final class InterestTagTaxonomy {

    private InterestTagTaxonomy() {
    }

    public static List<String> expand(Collection<String> rawTags,
                                      String name,
                                      String description,
                                      BigDecimal price,
                                      Integer salesCount) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (rawTags != null) {
            for (String tag : rawTags) {
                add(tags, tag);
            }
        }

        String text = normalizeText(name) + " " + normalizeText(description) + " " + String.join(" ", tags);
        addCategoryTags(tags, text);
        addScenarioTags(tags, text);
        addPriceTags(tags, price);
        addPopularityTags(tags, salesCount);
        return new java.util.ArrayList<>(tags);
    }

    public static List<String> expandUserTags(Collection<String> rawTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (rawTags != null) {
            for (String tag : rawTags) {
                add(tags, tag);
            }
        }
        String text = String.join(" ", tags);
        addCategoryTags(tags, text);
        addScenarioTags(tags, text);
        return new java.util.ArrayList<>(tags);
    }

    public static double weightedOverlap(Collection<String> userTags, Collection<String> productTags) {
        if (userTags == null || userTags.isEmpty() || productTags == null || productTags.isEmpty()) {
            return 0D;
        }
        Set<String> userSet = new LinkedHashSet<>(expandUserTags(userTags));
        Set<String> productSet = new LinkedHashSet<>(expand(productTags, null, null, null, null));
        if (userSet.isEmpty() || productSet.isEmpty()) {
            return 0D;
        }
        double hit = 0D;
        double total = 0D;
        for (String tag : userSet) {
            double weight = tagWeight(tag);
            total += weight;
            if (productSet.contains(tag)) {
                hit += weight;
            }
        }
        return total <= 0D ? 0D : Math.min(1D, hit / total);
    }

    private static void addCategoryTags(Set<String> tags, String text) {
        if (containsAny(text, "生鲜", "水果", "蔬菜", "鲜食", "食品", "零食", "饮料", "坚果", "茶", "咖啡")) {
            add(tags, "类目:食品");
        }
        if (containsAny(text, "生鲜", "水果", "蔬菜", "鲜食")) {
            add(tags, "细分:生鲜");
        }
        if (containsAny(text, "图书", "书", "教材", "小说", "文具", "笔记本", "办公", "学习")) {
            add(tags, "类目:图书文具");
        }
        if (containsAny(text, "手机", "电脑", "数码", "耳机", "键盘", "鼠标", "平板", "相机")) {
            add(tags, "类目:数码");
        }
        if (containsAny(text, "护肤", "美妆", "口红", "面膜", "防晒", "香水", "彩妆")) {
            add(tags, "类目:美妆护肤");
        }
        if (containsAny(text, "服饰", "穿搭", "鞋", "包", "外套", "卫衣", "裙", "裤")) {
            add(tags, "类目:服饰穿搭");
        }
        if (containsAny(text, "家电", "厨房", "家居", "清洁", "收纳", "电器")) {
            add(tags, "类目:家居家电");
        }
        if (containsAny(text, "户外", "运动", "露营", "旅行", "健身", "骑行")) {
            add(tags, "类目:运动户外");
        }
    }

    private static void addScenarioTags(Set<String> tags, String text) {
        if (containsAny(text, "通勤", "办公", "学习", "轻便", "便携")) {
            add(tags, "场景:通勤办公");
        }
        if (containsAny(text, "露营", "户外", "旅行", "便携", "防水")) {
            add(tags, "场景:户外旅行");
        }
        if (containsAny(text, "礼盒", "送礼", "节日", "生日", "伴手礼")) {
            add(tags, "场景:送礼");
        }
        if (containsAny(text, "家用", "家庭", "厨房", "清洁", "收纳")) {
            add(tags, "场景:居家");
        }
        if (containsAny(text, "新手", "入门", "基础", "学生")) {
            add(tags, "层级:入门");
        }
        if (containsAny(text, "旗舰", "高端", "专业", "进阶", "精选")) {
            add(tags, "层级:专业");
        }
    }

    private static void addPriceTags(Set<String> tags, BigDecimal price) {
        if (price == null) {
            return;
        }
        double value = price.doubleValue();
        if (value <= 50D) {
            add(tags, "价格:平价");
        } else if (value <= 300D) {
            add(tags, "价格:中档");
        } else if (value <= 1000D) {
            add(tags, "价格:品质");
        } else {
            add(tags, "价格:高端");
        }
    }

    private static void addPopularityTags(Set<String> tags, Integer salesCount) {
        if (salesCount == null) {
            return;
        }
        if (salesCount >= 300) {
            add(tags, "热度:热销");
        } else if (salesCount <= 10) {
            add(tags, "热度:长尾");
        }
    }

    private static double tagWeight(String tag) {
        if (tag.startsWith("类目:")) {
            return 2.4D;
        }
        if (tag.startsWith("细分:")) {
            return 2.0D;
        }
        if (tag.startsWith("场景:")) {
            return 1.6D;
        }
        if (tag.startsWith("价格:")) {
            return 1.2D;
        }
        if (tag.startsWith("层级:")) {
            return 1.1D;
        }
        if (tag.startsWith("热度:")) {
            return 0.8D;
        }
        return 1D;
    }

    private static boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return Arrays.stream(keywords).anyMatch(text::contains);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void add(Set<String> tags, String tag) {
        if (!StringUtils.hasText(tag)) {
            return;
        }
        tags.add(tag.trim().toLowerCase(Locale.ROOT));
    }
}
