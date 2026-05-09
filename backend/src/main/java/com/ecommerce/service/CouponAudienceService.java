package com.ecommerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.entity.AnalyticsKmeansTask;
import com.ecommerce.entity.AnalyticsKmeansUserResult;
import com.ecommerce.entity.Coupon;
import com.ecommerce.mapper.AnalyticsKmeansTaskMapper;
import com.ecommerce.mapper.AnalyticsKmeansUserResultMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CouponAudienceService {

    public static final int AUDIENCE_ALL = 0;
    public static final int AUDIENCE_SEGMENT = 1;
    public static final int AUDIENCE_USER = 2;

    private static final String TASK_STATUS_SUCCESS = "success";

    private final AnalyticsKmeansTaskMapper analyticsKmeansTaskMapper;
    private final AnalyticsKmeansUserResultMapper analyticsKmeansUserResultMapper;

    public CouponAudienceService(AnalyticsKmeansTaskMapper analyticsKmeansTaskMapper,
                                 AnalyticsKmeansUserResultMapper analyticsKmeansUserResultMapper) {
        this.analyticsKmeansTaskMapper = analyticsKmeansTaskMapper;
        this.analyticsKmeansUserResultMapper = analyticsKmeansUserResultMapper;
    }

    public List<Coupon> filterEligibleCoupons(Long userId, Collection<Coupon> coupons) {
        if (coupons == null || coupons.isEmpty()) {
            return Collections.emptyList();
        }

        String userSegmentCode = null;
        boolean segmentLoaded = false;
        List<Coupon> eligibleCoupons = new ArrayList<>();
        for (Coupon coupon : coupons) {
            if (coupon == null) {
                continue;
            }

            int audienceType = normalizeAudienceType(coupon.getAudienceType());
            if (audienceType == AUDIENCE_ALL) {
                eligibleCoupons.add(coupon);
                continue;
            }

            if (!segmentLoaded && audienceType == AUDIENCE_SEGMENT) {
                userSegmentCode = resolveLatestUserSegmentCode(userId);
                segmentLoaded = true;
            }

            if (isEligibleForCoupon(coupon, userId, userSegmentCode)) {
                eligibleCoupons.add(coupon);
            }
        }
        return eligibleCoupons;
    }

    public boolean isEligibleForCoupon(Coupon coupon, Long userId) {
        return isEligibleForCoupon(coupon, userId, null);
    }

    public boolean isEligibleForCoupon(Coupon coupon, Long userId, String userSegmentCode) {
        if (coupon == null) {
            return false;
        }

        int audienceType = normalizeAudienceType(coupon.getAudienceType());
        if (audienceType == AUDIENCE_ALL) {
            return true;
        }
        if (userId == null || userId <= 0) {
            return false;
        }

        if (audienceType == AUDIENCE_SEGMENT) {
            String effectiveSegmentCode = normalizeSegmentCode(userSegmentCode);
            if (!StringUtils.hasText(effectiveSegmentCode)) {
                effectiveSegmentCode = resolveLatestUserSegmentCode(userId);
            }
            return StringUtils.hasText(effectiveSegmentCode)
                    && parseSegmentCodes(coupon.getTargetSegmentCodes()).contains(effectiveSegmentCode);
        }

        if (audienceType == AUDIENCE_USER) {
            return parseTargetUserIds(coupon.getTargetUserIds()).contains(userId);
        }

        return true;
    }

    public String resolveLatestUserSegmentCode(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }

        AnalyticsKmeansTask latestTask = analyticsKmeansTaskMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsKmeansTask>()
                        .eq(AnalyticsKmeansTask::getStatus, TASK_STATUS_SUCCESS)
                        .orderByDesc(AnalyticsKmeansTask::getSnapshotDate)
                        .orderByDesc(AnalyticsKmeansTask::getId)
                        .last("LIMIT 1"));
        if (latestTask == null || latestTask.getId() == null) {
            return null;
        }

        AnalyticsKmeansUserResult userResult = analyticsKmeansUserResultMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsKmeansUserResult>()
                        .eq(AnalyticsKmeansUserResult::getTaskId, latestTask.getId())
                        .eq(AnalyticsKmeansUserResult::getUserId, userId)
                        .last("LIMIT 1"));
        if (userResult == null) {
            return null;
        }

        return normalizeSegmentCode(userResult.getSegmentCode());
    }

    public int normalizeAudienceType(Integer audienceType) {
        if (audienceType == null) {
            return AUDIENCE_ALL;
        }
        if (audienceType != AUDIENCE_ALL
                && audienceType != AUDIENCE_SEGMENT
                && audienceType != AUDIENCE_USER) {
            return AUDIENCE_ALL;
        }
        return audienceType;
    }

    public String normalizeSegmentCodeList(String rawValue) {
        return String.join(",", parseSegmentCodes(rawValue));
    }

    public String normalizeTargetUserIdList(String rawValue) {
        return parseTargetUserIds(rawValue).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public Set<String> parseSegmentCodes(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Collections.emptySet();
        }

        Set<String> values = new LinkedHashSet<>();
        for (String token : rawValue.split("[,，;；\\s]+")) {
            String normalized = normalizeSegmentCode(token);
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }

    public Set<Long> parseTargetUserIds(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Collections.emptySet();
        }

        Set<Long> userIds = new LinkedHashSet<>();
        for (String token : rawValue.split("[,，;；\\s]+")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            try {
                long userId = Long.parseLong(token.trim());
                if (userId > 0) {
                    userIds.add(userId);
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid ids and let validation decide whether the final result is empty.
            }
        }
        return userIds;
    }

    private String normalizeSegmentCode(String segmentCode) {
        if (!StringUtils.hasText(segmentCode)) {
            return null;
        }
        return segmentCode.trim().toUpperCase(Locale.ROOT);
    }
}
