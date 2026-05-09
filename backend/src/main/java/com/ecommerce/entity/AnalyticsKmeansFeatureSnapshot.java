package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "analytics_kmeans_feature_snapshot", autoResultMap = true)
public class AnalyticsKmeansFeatureSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private LocalDate snapshotDate;

    private Long userId;

    @TableField("order_count_90d")
    private Integer orderCount90d;

    @TableField("order_amount_90d")
    private BigDecimal orderAmount90d;

    @TableField("avg_order_amount_90d")
    private BigDecimal avgOrderAmount90d;

    @TableField("distinct_category_count_90d")
    private Integer distinctCategoryCount90d;

    @TableField("behavior_count_30d")
    private Integer behaviorCount30d;

    @TableField("view_count_30d")
    private Integer viewCount30d;

    @TableField("cart_count_30d")
    private Integer cartCount30d;

    @TableField("favorite_count_30d")
    private Integer favoriteCount30d;

    @TableField("purchase_behavior_count_30d")
    private Integer purchaseBehaviorCount30d;

    @TableField("active_days_30d")
    private Integer activeDays30d;

    @TableField("avg_duration_30d")
    private BigDecimal avgDuration30d;

    private Integer recencyOrderDays;

    private Integer recencyBehaviorDays;

    private Integer tenureDays;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rawFeatures;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> normalizedFeatures;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
