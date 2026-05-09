package com.ecommerce.recommendation;

import com.ecommerce.entity.AnalyticsRecommendationExposure;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.AnalyticsRecommendationExposureMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ABTestFrameworkTest {

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private UserBehaviorMapper userBehaviorMapper;

    @Mock
    private AnalyticsRecommendationExposureMapper analyticsRecommendationExposureMapper;

    @InjectMocks
    private ABTestFramework framework;

    @Test
    void assignGroupShouldSpreadSequentialUsersAcrossAllExperimentGroups() {
        Set<String> groups = new HashSet<>();

        for (long userId = 1; userId <= 60; userId++) {
            groups.add(framework.assignGroup(userId).code);
        }

        assertEquals(3, groups.size());
    }

    @Test
    void getReportShouldFallbackToHistoricalBehaviorWhenRedisMetricsAreEmpty() {
        when(analyticsRecommendationExposureMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userBehaviorMapper.selectList(any())).thenReturn(Arrays.asList(
                behavior(11L, "view"),
                behavior(12L, "favorite"),
                behavior(13L, "cart"),
                behavior(14L, "purchase")
        ));

        Map<String, Object> report = framework.getReport();

        assertEquals(1L, sum(report, "totalExposures"));
        assertEquals(3L, sum(report, "totalClicks"));
        assertEquals(2L, sum(report, "totalAddToCarts"));
        assertEquals(1L, sum(report, "totalPurchases"));
        assertEquals(4L, sum(report, "uniqueUsers"));
        assertTrue(countGroupsWithUsers(report) >= 2);
    }

    @Test
    void getReportShouldPreferExposureFactTableWhenPresent() {
        when(analyticsRecommendationExposureMapper.selectList(any())).thenReturn(Arrays.asList(
                exposure(11L, true, false, false),
                exposure(12L, true, true, false),
                exposure(13L, true, true, true)
        ));

        Map<String, Object> report = framework.getReport();

        assertEquals(3L, sum(report, "totalExposures"));
        assertEquals(3L, sum(report, "totalClicks"));
        assertEquals(2L, sum(report, "totalAddToCarts"));
        assertEquals(1L, sum(report, "totalPurchases"));
        assertEquals(3L, sum(report, "uniqueUsers"));
    }

    private long sum(Map<String, Object> report, String key) {
        long total = 0L;
        for (Object value : report.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> groupReport = (Map<String, Object>) value;
            Object metric = groupReport.get(key);
            if (metric instanceof Number) {
                total += ((Number) metric).longValue();
            }
        }
        return total;
    }

    private long countGroupsWithUsers(Map<String, Object> report) {
        long count = 0L;
        for (Object value : report.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> groupReport = (Map<String, Object>) value;
            Object users = groupReport.get("uniqueUsers");
            if (users instanceof Number && ((Number) users).longValue() > 0) {
                count++;
            }
        }
        return count;
    }

    private UserBehavior behavior(Long userId, String behaviorType) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setProductId(100L + userId);
        behavior.setBehaviorType(behaviorType);
        return behavior;
    }

    private AnalyticsRecommendationExposure exposure(Long userId,
                                                     boolean clicked,
                                                     boolean carted,
                                                     boolean purchased) {
        AnalyticsRecommendationExposure exposure = new AnalyticsRecommendationExposure();
        exposure.setUserId(userId);
        exposure.setProductId(100L + userId);
        exposure.setExperimentGroup("A");
        if (clicked) {
            exposure.setClickTime(java.time.LocalDateTime.now());
        }
        if (carted) {
            exposure.setCartTime(java.time.LocalDateTime.now());
        }
        if (purchased) {
            exposure.setPurchaseTime(java.time.LocalDateTime.now());
        }
        return exposure;
    }
}
