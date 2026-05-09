package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analytics_recommendation_exposure")
public class AnalyticsRecommendationExposure {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String exposureToken;

    private String requestToken;

    private Long userId;

    private Long productId;

    private String scene;

    private Integer rankNo;

    private String algorithm;

    private String sourceType;

    private String reasonType;

    private String modelVersion;

    private String experimentGroup;

    private String segmentCode;

    private String segmentName;

    private LocalDateTime exposureTime;

    private LocalDateTime clickTime;

    private LocalDateTime favoriteTime;

    private LocalDateTime cartTime;

    private LocalDateTime purchaseTime;

    private Long orderId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
