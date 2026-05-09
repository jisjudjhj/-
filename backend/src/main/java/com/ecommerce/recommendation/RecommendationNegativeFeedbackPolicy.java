package com.ecommerce.recommendation;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

public final class RecommendationNegativeFeedbackPolicy {

    private RecommendationNegativeFeedbackPolicy() {
    }

    public static String normalizeDislikeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "not_interested";
        }
        String value = reason.trim().toLowerCase(Locale.ROOT);
        if (value.contains("price") || value.contains("价格")) {
            return "price_high";
        }
        if (value.contains("category") || value.contains("类目")) {
            return "category_dislike";
        }
        if (value.contains("duplicate") || value.contains("重复")) {
            return "duplicate";
        }
        if (value.contains("already") || value.contains("买过")) {
            return "already_bought";
        }
        return value;
    }

    public static int productWeight(String reason) {
        String normalized = normalizeDislikeReason(reason);
        if ("duplicate".equals(normalized)) {
            return 4;
        }
        if ("already_bought".equals(normalized)) {
            return 3;
        }
        if ("category_dislike".equals(normalized)) {
            return 3;
        }
        if ("price_high".equals(normalized)) {
            return 2;
        }
        return 2;
    }

    public static String priceBand(BigDecimal price) {
        if (price == null) {
            return "";
        }
        double value = price.doubleValue();
        if (value < 50D) {
            return "p0_50";
        }
        if (value < 100D) {
            return "p50_100";
        }
        if (value < 300D) {
            return "p100_300";
        }
        if (value < 800D) {
            return "p300_800";
        }
        return "p800_plus";
    }
}
