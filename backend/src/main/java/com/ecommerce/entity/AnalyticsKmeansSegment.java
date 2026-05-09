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
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "analytics_kmeans_segment", autoResultMap = true)
public class AnalyticsKmeansSegment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private LocalDate snapshotDate;

    private String segmentCode;

    private String segmentName;

    private String segmentDescription;

    private String llmSummary;

    private String operationSuggestion;

    private Long userCount;

    private BigDecimal percentage;

    @TableField("avg_order_count_90d")
    private BigDecimal avgOrderCount90d;

    @TableField("avg_order_amount_90d")
    private BigDecimal avgOrderAmount90d;

    @TableField("avg_behavior_count_30d")
    private BigDecimal avgBehaviorCount30d;

    @TableField("avg_active_days_30d")
    private BigDecimal avgActiveDays30d;

    private BigDecimal avgRecencyDays;

    private BigDecimal avgPricePerOrder;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> featureCenter;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> topCategories;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> topTags;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
