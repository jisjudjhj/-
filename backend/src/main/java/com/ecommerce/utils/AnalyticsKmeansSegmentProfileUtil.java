package com.ecommerce.utils;

import com.ecommerce.entity.AnalyticsKmeansSegment;

import java.math.BigDecimal;

public final class AnalyticsKmeansSegmentProfileUtil {

    public static final String PROFILE_SLEEPING = "sleeping";
    public static final String PROFILE_HIGH_VALUE = "high_value";
    public static final String PROFILE_ACTIVE_INTEREST = "active_interest";
    public static final String PROFILE_PRICE_SENSITIVE = "price_sensitive";
    public static final String PROFILE_LOYAL = "loyal";
    public static final String PROFILE_BALANCED = "balanced";

    private AnalyticsKmeansSegmentProfileUtil() {
    }

    public static boolean isLowActivitySegment(AnalyticsKmeansSegment segment) {
        return PROFILE_SLEEPING.equals(resolveProfile(segment));
    }

    public static String resolveProfile(AnalyticsKmeansSegment segment) {
        if (segment == null) {
            return PROFILE_BALANCED;
        }

        BigDecimal avgRecencyDays = safeDecimal(segment.getAvgRecencyDays());
        BigDecimal avgBehaviorCount = safeDecimal(segment.getAvgBehaviorCount30d());
        BigDecimal avgOrderAmount = safeDecimal(segment.getAvgOrderAmount90d());
        BigDecimal avgOrderCount = safeDecimal(segment.getAvgOrderCount90d());
        BigDecimal avgPricePerOrder = safeDecimal(segment.getAvgPricePerOrder());
        BigDecimal avgActiveDays = safeDecimal(segment.getAvgActiveDays30d());

        if (avgRecencyDays.compareTo(new BigDecimal("45")) >= 0
                && avgBehaviorCount.compareTo(new BigDecimal("5")) < 0) {
            return PROFILE_SLEEPING;
        }
        if (avgOrderAmount.compareTo(new BigDecimal("500")) >= 0
                || avgPricePerOrder.compareTo(new BigDecimal("300")) >= 0
                || avgOrderCount.compareTo(new BigDecimal("4")) >= 0) {
            return PROFILE_HIGH_VALUE;
        }
        if (avgBehaviorCount.compareTo(new BigDecimal("18")) >= 0
                && avgOrderCount.compareTo(new BigDecimal("2")) < 0) {
            return PROFILE_ACTIVE_INTEREST;
        }
        if (avgPricePerOrder.compareTo(BigDecimal.ZERO) > 0
                && avgPricePerOrder.compareTo(new BigDecimal("120")) <= 0
                && avgBehaviorCount.compareTo(new BigDecimal("8")) >= 0) {
            return PROFILE_PRICE_SENSITIVE;
        }
        if (avgOrderCount.compareTo(new BigDecimal("3")) >= 0
                && avgActiveDays.compareTo(new BigDecimal("8")) >= 0) {
            return PROFILE_LOYAL;
        }
        return PROFILE_BALANCED;
    }

    private static BigDecimal safeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
