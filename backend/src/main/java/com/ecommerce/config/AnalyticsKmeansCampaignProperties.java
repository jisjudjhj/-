package com.ecommerce.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "analytics.kmeans.campaign")
public class AnalyticsKmeansCampaignProperties {

    private boolean enabled = true;

    private boolean autoProcessEnabled = false;

    private Long defaultCouponId;

    private long schedulerFixedDelayMs = 60000L;

    private int scanLimit = 5;

    private int recentDays = 7;

    private String notificationTitle = "专属唤醒优惠券已到账";

    private String notificationMessageType = "promotion";
}
